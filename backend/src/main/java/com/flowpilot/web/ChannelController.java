package com.flowpilot.web;

import com.flowpilot.auth.RequireRole;
import com.flowpilot.channel.FeishuClient;
import com.flowpilot.channel.MockChannelService;
import com.flowpilot.common.ApiResponse;
import com.flowpilot.common.BizException;
import com.flowpilot.config.FlowPilotProperties;
import com.flowpilot.model.User;
import com.flowpilot.service.ChannelSyncService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 渠道接口（PRD 3.2）：渠道状态、飞书群列表、手动同步、演示数据生成。
 */
@RestController
@RequestMapping("/api/v1/channels")
public class ChannelController {

    private final FeishuClient feishuClient;
    private final ChannelSyncService channelSyncService;
    private final com.flowpilot.channel.EmailChannelService emailChannelService;
    private final FlowPilotProperties props;

    public ChannelController(FeishuClient feishuClient,
                             ChannelSyncService channelSyncService,
                             com.flowpilot.channel.EmailChannelService emailChannelService,
                             FlowPilotProperties props) {
        this.feishuClient = feishuClient;
        this.channelSyncService = channelSyncService;
        this.emailChannelService = emailChannelService;
        this.props = props;
    }

    /** 各渠道接入状态（含配置与凭证提示） */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(Map.of(
                "feishu", Map.of(
                        "configured", feishuClient.configured(),
                        "appId", mask(props.getFeishu().getAppId()),
                        "supported", true,
                        "note", "群消息自动同步 + 事件回调 + 一键深链沟通"),
                "wechat", Map.of(
                        "configured", true,
                        "supported", true,
                        "watchDir", props.getWechat().getWatchDir(),
                        "watchEnabled", props.getWechat().isWatchEnabled(),
                        "ocr", props.getWechat().getOcr().getProvider(),
                        "note", "个人微信无官方 API：文件夹监控自动导入 + 截图 OCR（见 docs/06）"),
                "email", Map.of(
                        "configured", emailChannelService.enabled(),
                        "username", mask(props.getEmail().getUsername()),
                        "supported", true,
                        "note", "IMAP 定时拉取项目相关邮件（主题/发件人匹配项目），作为 AI 分析数据源"),
                "mock", Map.of(
                        "configured", true,
                        "supported", true,
                        "note", "演示渠道：无凭证生成仿真群聊")));
    }

    /** 机器人所在飞书群列表（绑定渠道用；tenantCode 缺省为主组织） */
    @GetMapping("/feishu/chats")
    public ApiResponse<List<Map<String, String>>> feishuChats(@RequestParam(required = false) String tenantCode) {
        String tenant = tenantCode == null ? "default" : tenantCode;
        if (!feishuClient.tenantConfig(tenant).configured()) {
            throw new BizException(50030, "飞书租户[" + tenant + "]未配置凭证");
        }
        return ApiResponse.ok(feishuClient.listBotChats(tenant));
    }

    /** 全部飞书租户列表（多租户管理） */
    @GetMapping("/feishu/tenants")
    public ApiResponse<List<Map<String, Object>>> feishuTenants() {
        return ApiResponse.ok(feishuClient.listTenants().stream()
                .map(t -> Map.<String, Object>of(
                        "code", t.code(), "name", t.name(), "configured", t.configured()))
                .toList());
    }

    /**
     * 飞书凭证自检（接入向导用）：验证指定租户的 app-id/app-secret 能否换取 token，
     * 并返回机器人所在群列表。
     */
    @GetMapping("/feishu/test")
    public ApiResponse<Map<String, Object>> feishuTest(@RequestParam(required = false) String tenantCode) {
        String tenant = tenantCode == null ? "default" : tenantCode;
        if (!feishuClient.tenantConfig(tenant).configured()) {
            return ApiResponse.ok(Map.of(
                    "ok", false,
                    "message", "飞书租户[" + tenant + "]尚未配置凭证：见 docs/07 创建应用后填入配置"));
        }
        try {
            List<Map<String, String>> chats = feishuClient.listBotChats(tenant);
            return ApiResponse.ok(Map.of(
                    "ok", true,
                    "message", "飞书租户[" + tenant + "]凭证有效 ✓（如群列表为空，请将机器人加入目标群并发布应用版本）",
                    "chats", chats));
        } catch (Exception e) {
            return ApiResponse.ok(Map.of(
                    "ok", false,
                    "message", "飞书租户[" + tenant + "]凭证校验失败：" + e.getMessage()
                            + "（请检查 App ID/Secret 是否正确、应用是否已发布）"));
        }
    }

    /** 手动触发全渠道同步 */
    @RequireRole(User.Role.MANAGER)
    @PostMapping("/sync")
    public ApiResponse<Map<String, Object>> syncAll() {
        int n = channelSyncService.syncAll();
        int emails = emailChannelService.enabled() ? emailChannelService.sync() : 0;
        return ApiResponse.ok(Map.of("syncedMessages", n, "syncedEmails", emails));
    }

    private String mask(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        return s.length() <= 4 ? "****" : s.substring(0, 3) + "****" + s.substring(s.length() - 3);
    }
}
