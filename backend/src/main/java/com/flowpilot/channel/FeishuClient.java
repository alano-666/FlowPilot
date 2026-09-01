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
import java.util.concurrent.ConcurrentHashMap;

/**
 * 飞书开放平台客户端（多租户版）：
 *
 * 一台 FlowPilot 可同时接入多个飞书组织。每个组织在自己的开发者后台
 * 创建一套自建应用，通过 flowpilot.feishu.tenants 配置（code 唯一）：
 *   - 单租户兼容：不配 tenants 时，code=default 使用顶级 app-id/app-secret；
 *   - 事件回调按租户代码路由：POST /api/v1/webhooks/feishu/events/{tenantCode}；
 *   - 每个租户独立缓存 tenant_access_token。
 */
@Component
public class FeishuClient {

    private static final Logger log = LoggerFactory.getLogger(FeishuClient.class);
    private static final String OPEN_API = "https://open.feishu.cn/open-apis";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final String DEFAULT_TENANT = "default";

    private final FlowPilotProperties props;
    private final RestClient client;

    /** 每租户 token 缓存 */
    private record TokenInfo(String token, long expireAt) {
    }

    private final ConcurrentHashMap<String, TokenInfo> tokens = new ConcurrentHashMap<>();

    public FeishuClient(FlowPilotProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.client = RestClient.builder().requestFactory(factory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // ---------- 租户解析 ----------

    /** 默认租户（主组织）是否已配置凭证 */
    public boolean configured() {
        return tenantConfig(DEFAULT_TENANT).configured();
    }

    /** 解析租户配置：default 用顶级字段，其余查 tenants 列表 */
    public TenantConfig tenantConfig(String tenantCode) {
        String code = tenantCode == null || tenantCode.isBlank() ? DEFAULT_TENANT : tenantCode;
        FlowPilotProperties.Feishu f = props.getFeishu();
        if (!DEFAULT_TENANT.equals(code)) {
            for (FlowPilotProperties.Tenant t : f.getTenants()) {
                if (t.getCode().equals(code)) {
                    return new TenantConfig(code, t.getName(), t.getAppId(), t.getAppSecret(),
                            t.getEncryptKey(), t.getVerificationToken());
                }
            }
            throw new BizException(50030, "飞书租户未配置: " + code);
        }
        return new TenantConfig(DEFAULT_TENANT, "默认组织", f.getAppId(), f.getAppSecret(),
                f.getEncryptKey(), f.getVerificationToken());
    }

    public List<TenantConfig> listTenants() {
        List<TenantConfig> out = new ArrayList<>();
        TenantConfig def = tenantConfig(DEFAULT_TENANT);
        if (def.appId() != null && !def.appId().isBlank()) {
            out.add(def);
        }
        for (FlowPilotProperties.Tenant t : props.getFeishu().getTenants()) {
            out.add(new TenantConfig(t.getCode(), t.getName(), t.getAppId(), t.getAppSecret(),
                    t.getEncryptKey(), t.getVerificationToken()));
        }
        return out;
    }

    public record TenantConfig(String code, String name, String appId, String appSecret,
                               String encryptKey, String verificationToken) {
        public boolean configured() {
            return appId != null && !appId.isBlank() && appSecret != null && !appSecret.isBlank();
        }
    }

    // ---------- Token ----------

    private String tenantAccessToken(String tenantCode) {
        TenantConfig cfg = tenantConfig(tenantCode);
        TokenInfo cached = tokens.get(cfg.code());
        if (cached != null && System.currentTimeMillis() < cached.expireAt() - 60_000) {
            return cached.token();
        }
        synchronized (this) {
            cached = tokens.get(cfg.code());
            if (cached != null && System.currentTimeMillis() < cached.expireAt() - 60_000) {
                return cached.token();
            }
            JsonNode resp = client.post()
                    .uri(OPEN_API + "/auth/v3/tenant_access_token/internal")
                    .body(Map.of("app_id", cfg.appId(), "app_secret", cfg.appSecret()))
                    .retrieve().body(JsonNode.class);
            if (resp == null || resp.path("code").asInt() != 0) {
                throw new BizException(50030, "飞书租户[" + cfg.code() + "] token 获取失败: "
                        + (resp == null ? "空响应" : resp.path("msg").asText()));
            }
            String token = resp.path("tenant_access_token").asText();
            long expireAt = System.currentTimeMillis() + resp.path("expire").asLong() * 1000;
            tokens.put(cfg.code(), new TokenInfo(token, expireAt));
            return token;
        }
    }

    // ---------- 消息同步 ----------

    public record FeishuMessage(String messageId, String chatId, String senderId, String msgType,
                                String textContent, long createTimeMs) {
    }

    public record MessagePage(List<FeishuMessage> items, String pageToken, boolean hasMore) {
    }

    /** 分页拉取群聊消息，startTime 为 epoch 秒 */
    public MessagePage listMessages(String tenantCode, String chatId, Long startTimeSeconds, String pageToken) {
        StringBuilder uri = new StringBuilder(OPEN_API + "/im/v1/messages?container_id_type=chat&container_id=")
                .append(urlEncode(chatId)).append("&page_size=50");
        if (startTimeSeconds != null) {
            uri.append("&start_time=").append(startTimeSeconds);
        }
        if (pageToken != null && !pageToken.isBlank()) {
            uri.append("&page_token=").append(urlEncode(pageToken));
        }
        JsonNode resp = client.get().uri(uri.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantAccessToken(tenantCode))
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
    public void sendTextToChat(String tenantCode, String chatId, String text) {
        TenantConfig cfg = tenantConfig(tenantCode);
        if (!cfg.configured()) {
            log.info("[飞书模拟发送] tenant={} chat={} text={}", tenantCode, chatId, text);
            return;
        }
        JsonNode resp = client.post()
                .uri(OPEN_API + "/im/v1/messages?receive_id_type=chat_id")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantAccessToken(tenantCode))
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
    public String getUserName(String tenantCode, String openId) {
        JsonNode resp = client.get()
                .uri(OPEN_API + "/contact/v3/users/" + urlEncode(openId) + "?user_id_type=open_id")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantAccessToken(tenantCode))
                .retrieve().body(JsonNode.class);
        if (resp == null || resp.path("code").asInt() != 0) {
            return openId;
        }
        return resp.path("data").path("user").path("name").asText(openId);
    }

    /** 机器人所在群列表（数据源管理页绑定用） */
    public List<Map<String, String>> listBotChats(String tenantCode) {
        JsonNode resp = client.get()
                .uri(OPEN_API + "/im/v1/chats?page_size=100")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantAccessToken(tenantCode))
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
     * 事件回调解密（按租户的 encryptKey）。url_verification 返回 challenge，
     * 业务事件返回解密后 JSON 字符串。
     */
    public String decryptEvent(String tenantCode, String encryptBase64) {
        try {
            TenantConfig cfg = tenantConfig(tenantCode);
            byte[] keyBytes = sha256(cfg.encryptKey().getBytes(StandardCharsets.UTF_8));
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
    public String fetchDocRawContent(String tenantCode, String docToken) {
        JsonNode resp = client.get()
                .uri(OPEN_API + "/docx/v1/documents/" + urlEncode(docToken) + "/raw_content")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantAccessToken(tenantCode))
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
