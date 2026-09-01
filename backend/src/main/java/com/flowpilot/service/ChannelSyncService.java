package com.flowpilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpilot.channel.FeishuClient;
import com.flowpilot.model.Message;
import com.flowpilot.model.ProjectChannel;
import com.flowpilot.repository.ProjectChannelRepository;
import com.flowpilot.repository.ProjectRepository;
import com.flowpilot.repository.SyncStateRepository;
import com.flowpilot.model.SyncState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 渠道消息同步服务（PRD 3.2 多渠道沟通接入）：
 *  - 定时增量同步飞书群消息（水位线 + 分页游标）；
 *  - 事件回调实时入库（飞书 im.message.receive_v1）。
 */
@Service
public class ChannelSyncService {

    private static final Logger log = LoggerFactory.getLogger(ChannelSyncService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FeishuClient feishuClient;
    private final ProjectChannelRepository channelRepository;
    private final ProjectRepository projectRepository;
    private final SyncStateRepository syncStateRepository;
    private final MessageService messageService;
    private final AnalysisService analysisService;

    public ChannelSyncService(FeishuClient feishuClient,
                              ProjectChannelRepository channelRepository, ProjectRepository projectRepository,
                              SyncStateRepository syncStateRepository, MessageService messageService,
                              AnalysisService analysisService) {
        this.feishuClient = feishuClient;
        this.channelRepository = channelRepository;
        this.projectRepository = projectRepository;
        this.syncStateRepository = syncStateRepository;
        this.messageService = messageService;
        this.analysisService = analysisService;
    }

    /** 定时任务入口：同步所有启用渠道的增量消息 */
    public int syncAll() {
        int total = 0;
        for (ProjectChannel pc : channelRepository.findByChannelTypeAndSyncEnabledTrue(
                ProjectChannel.ChannelType.FEISHU)) {
            if (!feishuClient.configured()) {
                log.info("飞书未配置凭证，跳过同步");
                break;
            }
            try {
                total += syncFeishuChannel(pc);
            } catch (Exception e) {
                log.warn("飞书渠道同步失败 channel={}: {}", pc.getChannelId(), e.getMessage());
            }
        }
        return total;
    }

    /** 飞书群增量同步（水位线 + 分页，按渠道绑定的租户取凭证） */
    public int syncFeishuChannel(ProjectChannel pc) {
        String tenant = pc.getTenantCode() == null ? "default" : pc.getTenantCode();
        SyncState state = syncStateRepository.findByChannelTypeAndChannelId("FEISHU", pc.getChannelId())
                .orElseGet(() -> {
                    SyncState s = new SyncState();
                    s.setChannelType("FEISHU");
                    s.setChannelId(pc.getChannelId());
                    return s;
                });
        Long startTime = state.getLastSyncAt() == null ? null
                : state.getLastSyncAt().atZone(ZoneId.systemDefault()).toEpochSecond();
        String cursor = null;
        int count = 0;
        do {
            FeishuClient.MessagePage page = feishuClient.listMessages(tenant, pc.getChannelId(), startTime, cursor);
            count += ingestFeishuMessages(pc, tenant, page.items());
            cursor = page.pageToken();
            if (cursor != null && !cursor.isBlank()) {
                state.setLastCursor(cursor);
            }
            if (cursor == null || cursor.isBlank()) {
                break;
            }
        } while (true);
        state.setLastSyncAt(LocalDateTime.now());
        syncStateRepository.save(state);
        pc.setLastSyncAt(LocalDateTime.now());
        pc.setLastSyncCursor(state.getLastCursor());
        channelRepository.save(pc);
        if (count > 0) {
            log.info("飞书渠道同步完成 tenant={} chat={} 新增 {} 条", tenant, pc.getChannelId(), count);
        }
        return count;
    }

    private int ingestFeishuMessages(ProjectChannel pc, String tenant, List<FeishuClient.FeishuMessage> items) {
        List<Message> messages = new ArrayList<>();
        for (FeishuClient.FeishuMessage fm : items) {
            Message m = new Message();
            m.setProjectId(pc.getProjectId());
            m.setChannelType(Message.ChannelType.FEISHU);
            m.setChannelId(pc.getChannelId());
            m.setMsgId(fm.messageId());
            m.setSenderId(fm.senderId());
            m.setSenderName(fm.senderId() == null ? "未知" : feishuClient.getUserName(tenant, fm.senderId()));
            m.setContent(fm.textContent());
            m.setMsgType(toMsgType(fm.msgType()));
            m.setSentAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(fm.createTimeMs()), ZoneId.systemDefault()));
            m.setSource("SYNC");
            messages.add(m);
        }
        int saved = messageService.saveAll(messages);
        if (saved > 0) {
            analysisService.analyzeAsync(pc.getProjectId(), "EVENT");
        }
        return saved;
    }

    // ---------- 事件回调入库 ----------

    /** 飞书事件：im.message.receive_v1（按租户代码路由） */
    public void handleFeishuEvent(String tenantCode, JsonNode event) {
        String type = event.path("header").path("event_type").asText("");
        if (!"im.message.receive_v1".equals(type)) {
            return;
        }
        JsonNode msg = event.path("event").path("message");
        String chatId = msg.path("chat_id").asText("");
        String messageId = msg.path("message_id").asText("");
        if (chatId.isBlank() || messageId.isBlank()) {
            return;
        }
        String tenant = tenantCode == null ? "default" : tenantCode;
        channelRepository.findByChannelTypeAndSyncEnabledTrue(ProjectChannel.ChannelType.FEISHU)
                .stream()
                .filter(pc -> pc.getChannelId().equals(chatId)
                        && tenant.equals(pc.getTenantCode() == null ? "default" : pc.getTenantCode()))
                .findFirst()
                .ifPresent(pc -> {
                    Message m = new Message();
                    m.setProjectId(pc.getProjectId());
                    m.setChannelType(Message.ChannelType.FEISHU);
                    m.setChannelId(chatId);
                    m.setMsgId(messageId);
                    String senderId = msg.path("sender").path("id").asText(null);
                    m.setSenderId(senderId);
                    m.setSenderName(senderId == null ? "未知" : feishuClient.getUserName(tenant, senderId));
                    m.setContent(extractFeishuContent(msg));
                    m.setMsgType(toMsgType(msg.path("message_type").asText("text")));
                    m.setSentAt(LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(msg.path("create_time").asLong(0) * 1000),
                            ZoneId.systemDefault()));
                    m.setSource("EVENT");
                    if (messageService.save(m)) {
                        analysisService.analyzeAsync(pc.getProjectId(), "EVENT");
                    }
                });
    }

    private String extractFeishuContent(JsonNode msg) {
        try {
            JsonNode content = MAPPER.readTree(msg.path("content").asText("{}"));
            return switch (msg.path("message_type").asText("text")) {
                case "text" -> content.path("text").asText("");
                case "post" -> content.toString();
                case "image" -> "[图片消息]";
                case "file" -> "[文件消息]";
                default -> "[消息类型:" + msg.path("message_type").asText() + "]";
            };
        } catch (Exception e) {
            return msg.path("content").asText("");
        }
    }

    private String textOf(org.w3c.dom.Document doc, String tag) {
        var nodes = doc.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent();
    }

    private Message.MsgType toMsgType(String feishuType) {
        return switch (feishuType) {
            case "image" -> Message.MsgType.IMAGE;
            case "file" -> Message.MsgType.FILE;
            default -> Message.MsgType.TEXT;
        };
    }
}
