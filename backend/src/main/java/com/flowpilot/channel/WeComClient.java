package com.flowpilot.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpilot.common.BizException;
import com.flowpilot.config.FlowPilotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

/**
 * 企业微信客户端：
 *  - access_token 缓存（corpid + corpsecret）
 *  - 自建应用发消息（text）
 *  - 群机器人 webhook 推送
 *  - 回调消息 AES 加解密与签名校验（企业微信回调协议）
 *  - 会话存档：官方仅提供 C SDK（libWeWorkFinanceSdk），需企业开通会话存档功能后
 *    将 SDK 接入，本类保留 syncArchive 接口与接入文档指引（见 docs/07）
 */
@Component
public class WeComClient {

    private static final Logger log = LoggerFactory.getLogger(WeComClient.class);
    private static final String API = "https://qyapi.weixin.qq.com/cgi-bin";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FlowPilotProperties props;
    private final RestClient client;

    private volatile String accessToken;
    private volatile long tokenExpireAt;

    public WeComClient(FlowPilotProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.client = RestClient.builder().requestFactory(factory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public boolean configured() {
        FlowPilotProperties.WeCom w = props.getWecom();
        return w.getCorpId() != null && !w.getCorpId().isBlank()
                && w.getCorpSecret() != null && !w.getCorpSecret().isBlank();
    }

    private String accessToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireAt - 60_000) {
            return accessToken;
        }
        synchronized (this) {
            if (accessToken != null && System.currentTimeMillis() < tokenExpireAt - 60_000) {
                return accessToken;
            }
            FlowPilotProperties.WeCom w = props.getWecom();
            JsonNode resp = client.get()
                    .uri(API + "/gettoken?corpid=" + w.getCorpId() + "&corpsecret=" + w.getCorpSecret())
                    .retrieve().body(JsonNode.class);
            if (resp == null || resp.path("errcode").asInt() != 0) {
                throw new BizException(50031, "企微 token 获取失败: " + (resp == null ? "空响应" : resp.path("errmsg").asText()));
            }
            accessToken = resp.path("access_token").asText();
            tokenExpireAt = System.currentTimeMillis() + resp.path("expires_in").asLong() * 1000;
            return accessToken;
        }
    }

    /** 自建应用向成员发文本消息 */
    public void sendTextToUser(String userId, String text) {
        if (!configured()) {
            log.info("[企微模拟发送] user={} text={}", userId, text);
            return;
        }
        FlowPilotProperties.WeCom w = props.getWecom();
        JsonNode resp = client.post()
                .uri(API + "/message/send?access_token=" + accessToken())
                .body(Map.of(
                        "touser", userId,
                        "msgtype", "text",
                        "agentid", w.getAgentId(),
                        "text", Map.of("content", text)))
                .retrieve().body(JsonNode.class);
        if (resp == null || resp.path("errcode").asInt() != 0) {
            throw new BizException(50031, "企微消息发送失败: " + (resp == null ? "空响应" : resp.path("errmsg").asText()));
        }
    }

    /** 群机器人 webhook 推送 */
    public void sendWebhook(String webhookUrl, String text) {
        JsonNode resp = client.post()
                .uri(webhookUrl)
                .body(Map.of("msgtype", "text", "text", Map.of("content", text)))
                .retrieve().body(JsonNode.class);
        if (resp == null || resp.path("errcode").asInt() != 0) {
            throw new BizException(50031, "企微 webhook 发送失败: " + (resp == null ? "空响应" : resp.path("errmsg").asText()));
        }
    }

    /** 企微客户端深链（唤起会话，部分环境需降级为搜索 userid） */
    public String chatDeepLink(String userId) {
        return "wxwork://message?username=" + userId;
    }

    // ---------- 回调加解密 ----------

    /**
     * 回调消息解密（GET 验签与 POST 消息体共用）。
     * 企微协议：AESKey = Base64Decode(EncodingAESKey + "=")，
     * 明文 = random(16B) + msgLen(4B 大端) + msg + corpId，PKCS7 填充。
     */
    public String decryptCallback(String encryptBase64) {
        try {
            FlowPilotProperties.WeCom w = props.getWecom();
            byte[] aesKey = Base64.getDecoder().decode(w.getEncodingAesKey() + "=");
            byte[] encrypted = Base64.getDecoder().decode(encryptBase64);
            IvParameterSpec iv = new IvParameterSpec(aesKey, 0, 16);
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), iv);
            byte[] decrypted = cipher.doFinal(encrypted);
            // 去 PKCS7 填充
            int pad = decrypted[decrypted.length - 1] & 0xFF;
            byte[] unpadded = Arrays.copyOfRange(decrypted, 0, decrypted.length - pad);
            // 去 random(16) + msgLen(4)
            int msgLen = ((unpadded[16] & 0xFF) << 24) | ((unpadded[17] & 0xFF) << 16)
                    | ((unpadded[18] & 0xFF) << 8) | (unpadded[19] & 0xFF);
            return new String(unpadded, 20, msgLen, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BizException(50031, "企微回调解密失败: " + e.getMessage());
        }
    }

    /** 回调签名校验：sha1(token, timestamp, nonce, encrypt) 字典序拼接 */
    public boolean verifySignature(String signature, String timestamp, String nonce, String encrypt) {
        try {
            String[] arr = {props.getWecom().getToken(), timestamp, nonce, encrypt};
            Arrays.sort(arr);
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(String.join("", arr).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString().equals(signature);
        } catch (Exception e) {
            return false;
        }
    }

    /** 从企微回调消息 XML 中提取 Encrypt 节点 */
    public String extractEncrypt(String xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            return doc.getElementsByTagName("Encrypt").item(0).getTextContent();
        } catch (Exception e) {
            throw new BizException(50031, "企微回调报文解析失败: " + e.getMessage());
        }
    }

    // ---------- 会话存档 ----------

    /**
     * 会话存档增量同步。
     * 官方唯一合规通道：企业开通会话存档功能后，使用官方 libWeWorkFinanceSdk
     * （C SDK，.so/.dll）经 cgo/JNA 调用。当前未接入 SDK 时给出明确指引。
     */
    public int syncArchive(String chatId, java.util.function.Consumer<Map<String, Object>> onMessage) {
        throw new BizException(50032,
                "企业微信会话存档需企业开通官方会话存档功能并接入 libWeWorkFinanceSdk（见 docs/07-飞书企微接入指南.md 第 5 章）。"
                        + "替代方案：群机器人回调（@机器人消息）或引导客户使用飞书群。");
    }
}
