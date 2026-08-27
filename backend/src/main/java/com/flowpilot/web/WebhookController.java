package com.flowpilot.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpilot.auth.PublicApi;
import com.flowpilot.channel.FeishuClient;
import com.flowpilot.channel.WeComClient;
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
 *  - 企微：GET/POST /api/v1/webhooks/wecom/events（签名校验 + AES 解密，@机器人消息）
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FeishuClient feishuClient;
    private final WeComClient weComClient;
    private final ChannelSyncService channelSyncService;
    private final FlowPilotProperties props;

    public WebhookController(FeishuClient feishuClient, WeComClient weComClient,
                             ChannelSyncService channelSyncService, FlowPilotProperties props) {
        this.feishuClient = feishuClient;
        this.weComClient = weComClient;
        this.channelSyncService = channelSyncService;
        this.props = props;
    }

    @PublicApi
    @PostMapping("/feishu/events")
    public Object feishuEvent(@RequestBody String body) {
        try {
            JsonNode payload = MAPPER.readTree(body);
            // 加密模式：{"encrypt": "..."}
            if (payload.has("encrypt")) {
                payload = MAPPER.readTree(feishuClient.decryptEvent(payload.path("encrypt").asText()));
            }
            // URL 验证
            if ("url_verification".equals(payload.path("type").asText())) {
                return Map.of("challenge", payload.path("challenge").asText());
            }
            // 业务事件
            String token = payload.path("header").path("token").asText("");
            if (props.getFeishu().getVerificationToken() != null
                    && !props.getFeishu().getVerificationToken().isBlank()
                    && !props.getFeishu().getVerificationToken().equals(token)) {
                log.warn("飞书回调 token 校验失败");
                throw new BizException(50030, "verification token 校验失败");
            }
            channelSyncService.handleFeishuEvent(payload);
            return Map.of("code", 0);
        } catch (Exception e) {
            log.warn("飞书回调处理失败: {}", e.getMessage());
            throw new BizException(50030, "飞书回调处理失败: " + e.getMessage());
        }
    }

    /** 企微回调 GET 验证 */
    @PublicApi
    @GetMapping("/wecom/events")
    public String wecomVerify(@RequestParam("msg_signature") String signature,
                              @RequestParam("timestamp") String timestamp,
                              @RequestParam("nonce") String nonce,
                              @RequestParam("echostr") String echostr) {
        if (!weComClient.verifySignature(signature, timestamp, nonce, echostr)) {
            throw new BizException(50031, "企微回调签名校验失败");
        }
        return weComClient.decryptCallback(echostr);
    }

    /** 企微回调 POST（@机器人消息） */
    @PublicApi
    @PostMapping("/wecom/events")
    public String wecomEvent(@RequestParam("msg_signature") String signature,
                             @RequestParam("timestamp") String timestamp,
                             @RequestParam("nonce") String nonce,
                             @RequestBody String xml) {
        String encrypt = weComClient.extractEncrypt(xml);
        if (!weComClient.verifySignature(signature, timestamp, nonce, encrypt)) {
            throw new BizException(50031, "企微回调签名校验失败");
        }
        String plain = weComClient.decryptCallback(encrypt);
        log.debug("企微回调明文: {}", plain);
        channelSyncService.handleWecomCallback(plain);
        return "success";
    }
}
