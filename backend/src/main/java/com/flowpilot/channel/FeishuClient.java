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

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 飞书开放平台客户端（直连 HTTP API，不依赖官方 SDK）：
 *  - tenant_access_token 缓存与刷新
 *  - 群聊消息分页同步（im/v1/messages）
 *  - 应用机器人发消息（im/v1/messages create）
 *  - 群自定义机器人 webhook 推送
 *  - 事件回调 AES 解密（url_verification + im.message.receive_v1）
 *  - 飞书在线文档正文拉取（docx raw_content）
 *  - 一键沟通深链生成
 */
@Component
public class FeishuClient {

    private static final Logger log = LoggerFactory.getLogger(FeishuClient.class);
    private static final String OPEN_API = "https://open.feishu.cn/open-apis";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FlowPilotProperties props;
    private final RestClient client;

    private volatile String tenantToken;
    private volatile long tokenExpireAt;

    public FeishuClient(FlowPilotProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.client = RestClient.builder().requestFactory(factory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public boolean configured() {
        FlowPilotProperties.Feishu f = props.getFeishu();
        return f.getAppId() != null && !f.getAppId().isBlank()
                && f.getAppSecret() != null && !f.getAppSecret().isBlank();
    }

    // ---------- Token ----------

    private String tenantAccessToken() {
        if (tenantToken != null && System.currentTimeMillis() < tokenExpireAt - 60_000) {
            return tenantToken;
        }
        synchronized (this) {
            if (tenantToken != null && System.currentTimeMillis() < tokenExpireAt - 60_000) {
                return tenantToken;
            }
            FlowPilotProperties.Feishu f = props.getFeishu();
            JsonNode resp = client.post()
                    .uri(OPEN_API + "/auth/v3/tenant_access_token/internal")
                    .body(Map.of("app_id", f.getAppId(), "app_secret", f.getAppSecret()))
                    .retrieve().body(JsonNode.class);
            if (resp == null || resp.path("code").asInt() != 0) {
                throw new BizException(50030, "飞书 token 获取失败: " + (resp == null ? "空响应" : resp));
            }
            tenantToken = resp.path("tenant_access_token").asText();
            tokenExpireAt = System.currentTimeMillis() + resp.path("expire").asLong() * 1000;
            return tenantToken;
        }
    }

    // ---------- 消息同步 ----------

    public record FeishuMessage(String messageId, String chatId, String senderId, String msgType,
                                String textContent, long createTimeMs) {
    }

    public record MessagePage(List<FeishuMessage> items, String pageToken, boolean hasMore) {
    }

    /** 分页拉取群聊消息，startTime 为 epoch 秒 */
    public MessagePage listMessages(String chatId, Long startTimeSeconds, String pageToken) {
        StringBuilder uri = new StringBuilder(OPEN_API + "/im/v1/messages?container_id_type=chat&container_id=")
                .append(urlEncode(chatId)).append("&page_size=50");
        if (startTimeSeconds != null) {
            uri.append("&start_time=").append(startTimeSeconds);
        }
        if (pageToken != null && !pageToken.isBlank()) {
            uri.append("&page_token=").append(urlEncode(pageToken));
        }
        JsonNode resp = client.get().uri(uri.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantAccessToken())
                .retrieve().body(JsonNode.class);
        if (resp == null || resp.path("code").asInt() != 0) {
            throw new BizException(50030, "飞书消息拉取失败: " + (resp == null ? "空响应" : resp.path("msg").asText()));
        }
        JsonNode data = resp.path("data");
        List<FeishuMessage> items = new ArrayList<>();
        for (JsonNode item : data.path("items")) {
            items.add(new FeishuMessage(
                    item.path("message_id").asText(),
                    item.path("chat_id").asText(),
                    item.path("sender").path("id").asText(null),
                    item.path("msg_type").asText(),
                    extractText(item.path("msg_type").asText(), item.path("body").path("content").asText()),
                    item.path("create_time").asLong() * 1000));
        }
        return new MessagePage(items,
                data.path("page_token").asText(null),
                data.path("has_more").asBoolean(false));
    }

    /** 提取文本内容；post(富文本) 递归取 text 片段，图片/文件返回占位说明 */
    private String extractText(String msgType, String contentJson) {
        if (contentJson == null || contentJson.isBlank()) {
            return "";
        }
        try {
            JsonNode content = MAPPER.readTree(contentJson);
            return switch (msgType) {
                case "text" -> content.path("text").asText("");
                case "post" -> flattenPost(content.path("content"));
                case "image" -> "[图片消息]";
                case "file" -> "[文件消息] " + content.path("file_name").asText("");
                default -> "[消息类型:" + msgType + "]";
            };
        } catch (Exception e) {
            return contentJson;
        }
    }

    private String flattenPost(JsonNode node) {
        StringBuilder sb = new StringBuilder();
        if (node.isTextual()) {
            sb.append(node.asText());
        } else if (node.isArray() || node.isObject()) {
            node.forEach(child -> sb.append(flattenPost(child)));
        }
        return sb.toString();
    }

    // ---------- 发送消息 ----------

    /** 应用机器人向群聊发消息（需将机器人加入群并开通发消息权限） */
    public void sendTextToChat(String chatId, String text) {
        if (!configured()) {
            log.info("[飞书模拟发送] chat={} text={}", chatId, text);
            return;
        }
        JsonNode resp = client.post()
                .uri(OPEN_API + "/im/v1/messages?receive_id_type=chat_id")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantAccessToken())
                .body(Map.of(
                        "receive_id", chatId,
                        "msg_type", "text",
                        "content", MAPPER.createObjectNode().put("text", text).toString()))
                .retrieve().body(JsonNode.class);
        if (resp == null || resp.path("code").asInt() != 0) {
            throw new BizException(50030, "飞书消息发送失败: " + (resp == null ? "空响应" : resp.path("msg").asText()));
        }
    }

    /** 群自定义机器人 webhook 推送（通知摘要用，无需应用凭证） */
    public void sendWebhook(String webhookUrl, String text) {
        JsonNode resp = client.post()
                .uri(webhookUrl)
                .body(Map.of("msg_type", "text", "content", Map.of("text", text)))
                .retrieve().body(JsonNode.class);
        if (resp == null || resp.path("code").asInt() != 0) {
            throw new BizException(50030, "飞书 webhook 发送失败: " + (resp == null ? "空响应" : resp.path("msg").asText()));
        }
    }

    // ---------- 用户与群 ----------

    /** 按 open_id 查用户姓名 */
    public String getUserName(String openId) {
        JsonNode resp = client.get()
                .uri(OPEN_API + "/contact/v3/users/" + urlEncode(openId) + "?user_id_type=open_id")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantAccessToken())
                .retrieve().body(JsonNode.class);
        if (resp == null || resp.path("code").asInt() != 0) {
            return openId;
        }
        return resp.path("data").path("user").path("name").asText(openId);
    }

    /** 机器人所在群列表（数据源管理页绑定用） */
    public List<Map<String, String>> listBotChats() {
        JsonNode resp = client.get()
                .uri(OPEN_API + "/im/v1/chats?page_size=100")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantAccessToken())
                .retrieve().body(JsonNode.class);
        List<Map<String, String>> chats = new ArrayList<>();
        if (resp == null || resp.path("code").asInt() != 0) {
            return chats;
        }
        for (JsonNode item : resp.path("data").path("items")) {
            chats.add(Map.of(
                    "chat_id", item.path("chat_id").asText(),
                    "name", item.path("name").asText("")));
        }
        return chats;
    }

    /** 一键沟通深链（PRD 3.5） */
    public String chatDeepLink(String openId) {
        return "https://applink.feishu.cn/client/chat/open?openId=" + urlEncode(openId);
    }

    // ---------- 事件解密 ----------

    /**
     * 事件回调解密。url_verification 返回 challenge，业务事件返回解密后 JSON 字符串。
     */
    public String decryptEvent(String encryptBase64) {
        try {
            byte[] keyBytes = sha256(props.getFeishu().getEncryptKey().getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = Base64.getDecoder().decode(encryptBase64);
            IvParameterSpec iv = new IvParameterSpec(encrypted, 0, 16);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), iv);
            byte[] decrypted = cipher.doFinal(encrypted, 16, encrypted.length - 16);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BizException(50030, "飞书事件解密失败: " + e.getMessage());
        }
    }

    // ---------- 在线文档 ----------

    /** 拉取飞书在线文档纯文本（需应用获得文档读取权限） */
    public String fetchDocRawContent(String docToken) {
        JsonNode resp = client.get()
                .uri(OPEN_API + "/docx/v1/documents/" + urlEncode(docToken) + "/raw_content")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantAccessToken())
                .retrieve().body(JsonNode.class);
        if (resp == null || resp.path("code").asInt() != 0) {
            throw new BizException(50030, "飞书文档读取失败(请确认应用已开通文档权限并加入文档协作者): "
                    + (resp == null ? "空响应" : resp.path("msg").asText()));
        }
        return resp.path("data").path("content").asText("");
    }

    private String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
