package com.flowpilot.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpilot.auth.PublicApi;
import com.flowpilot.channel.FeishuClient;
import com.flowpilot.common.BizException;
import com.flowpilot.config.FlowPilotProperties;
import com.flowpilot.service.ChannelSyncService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 渠道事件回调（公开接口，签名/加密校验见各渠道官方协议）：
 *  - 飞书：POST /api/v1/webhooks/feishu/events（url_verification + im.message.receive_v1，支持 AES 解密）
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FeishuClient feishuClient;
    private final ChannelSyncService channelSyncService;
    private final FlowPilotProperties props;

    public WebhookController(FeishuClient feishuClient,
                             ChannelSyncService channelSyncService, FlowPilotProperties props) {
        this.feishuClient = feishuClient;
        this.channelSyncService = channelSyncService;
        this.props = props;
    }

    /**
     * 飞书事件回调（默认租户，兼容旧配置）：
     * 回调地址 https://你的公网地址/api/v1/webhooks/feishu/events
     */
    @PublicApi
    @PostMapping("/feishu/events")
    public Object feishuEvent(@RequestBody String body) {
        return feishuEventForTenant("default", body);
    }

    /**
     * 飞书事件回调（多租户，每个组织配独立路径段）：
     * 回调地址 https://你的公网地址/api/v1/webhooks/feishu/events/{tenantCode}
     * tenantCode 对应 flowpilot.feishu.tenants[].code
     */
    @PublicApi
    @PostMapping("/feishu/events/{tenantCode}")
    public Object feishuEventForTenant(@PathVariable String tenantCode, @RequestBody String body) {
        try {
            JsonNode payload = MAPPER.readTree(body);
            // 加密模式：{"encrypt": "..."}
            if (payload.has("encrypt")) {
                payload = MAPPER.readTree(feishuClient.decryptEvent(tenantCode, payload.path("encrypt").asText()));
            }
            // URL 验证
            if ("url_verification".equals(payload.path("type").asText())) {
                return Map.of("challenge", payload.path("challenge").asText());
            }
            // 业务事件（verification token 按租户校验）
            String token = payload.path("header").path("token").asText("");
            String expected = feishuClient.tenantConfig(tenantCode).verificationToken();
            if (expected != null && !expected.isBlank() && !expected.equals(token)) {
                log.warn("飞书回调 token 校验失败 tenant={}", tenantCode);
                throw new BizException(50030, "verification token 校验失败");
            }
            channelSyncService.handleFeishuEvent(tenantCode, payload);
            return Map.of("code", 0);
        } catch (Exception e) {
            log.warn("飞书回调处理失败 tenant={}: {}", tenantCode, e.getMessage());
            throw new BizException(50030, "飞书回调处理失败: " + e.getMessage());
        }
    }

}
