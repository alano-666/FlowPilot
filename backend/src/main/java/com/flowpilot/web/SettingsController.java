package com.flowpilot.web;

import com.flowpilot.ai.LlmFactory;
import com.flowpilot.common.ApiResponse;
import com.flowpilot.config.FlowPilotProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统设置接口：只读展示当前配置（脱敏），完整配置见 application.yml。
 */
@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final FlowPilotProperties props;
    private final LlmFactory llmFactory;

    public SettingsController(FlowPilotProperties props, LlmFactory llmFactory) {
        this.props = props;
        this.llmFactory = llmFactory;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> get() {
        return ApiResponse.ok(Map.of(
                "ai", Map.of(
                        "provider", props.getAi().getProvider(),
                        "activeProvider", llmFactory.get().name(),
                        "parallelism", props.getAi().getParallelism(),
                        "anthropicModel", props.getAi().getAnthropic().getModel(),
                        "openaiModel", props.getAi().getOpenai().getModel()),
                "notify", Map.of(
                        "digestCron", props.getNotify().getDigestCron(),
                        "syncCron", props.getNotify().getSyncCron(),
                        "slaCheckCron", props.getNotify().getSlaCheckCron(),
                        "feishuWebhookConfigured", !props.getNotify().getFeishuWebhook().isBlank(),
                        "wecomWebhookConfigured", !props.getNotify().getWecomWebhook().isBlank()),
                "wechat", Map.of(
                        "watchDir", props.getWechat().getWatchDir(),
                        "watchEnabled", props.getWechat().isWatchEnabled(),
                        "ocrProvider", props.getWechat().getOcr().getProvider()),
                "data", Map.of("retentionDays", props.getData().getRetentionDays()),
                "version", "1.0.0"));
    }
}
