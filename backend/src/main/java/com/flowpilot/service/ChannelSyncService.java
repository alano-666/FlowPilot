package com.flowpilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpilot.channel.FeishuClient;
import com.flowpilot.channel.WeComClient;
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
 *  - 事件回调实时入库（飞书 im.message.receive_v1、企微 @机器人消息）；
 *  - 企微会话存档走官方 SDK（见 WeComClient.syncArchive 文档指引）。
 */
@Service
public class ChannelSyncService {

    private static final Logger log = LoggerFactory.getLogger(ChannelSyncService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FeishuClient feishuClient;
    private final WeComClient weComClient;
    private final ProjectChannelRepository channelRepository;
    private final ProjectRepository projectRepository;
    private final SyncStateRepository syncStateRepository;
    private final MessageService messageService;
    private final AnalysisService analysisService;

    public ChannelSyncService(FeishuClient feishuClient, WeComClient weComClient,
                              ProjectChannelRepository channelRepository, ProjectRepository projectRepository,
                              SyncStateRepository syncStateRepository, MessageService messageService,
                              AnalysisService analysisService) {
        this.feishuClient = feishuClient;
        this.weComClient = weComClient;
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
        for (ProjectChannel pc : channelRepository.findByChannelTypeAndSyncEnabledTrue(
                ProjectChannel.ChannelType.WECOM)) {
            if (!weComClient.configured()) {
                log.info("企微未配置凭证，跳过同步");
                break;
            }
            try {
                total += syncWecomChannel(pc);
            } catch (Exception e) {
                log.warn("企微渠道同步失败 channel={}: {}", pc.getChannelId(), e.getMessage());
            }
        }
        return total;
    }

    /** 飞书群增量同步（水位线 + 分页） */
    public int syncFeishuChannel(ProjectChannel pc) {
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
            FeishuClient.MessagePage page = feishuClient.listMessages(pc.getChannelId(), startTime, cursor);
            count += ingestFeishuMessages(pc, page.items());
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
            log.info("飞书渠道同步完成 chat={} 新增 {} 条", pc.getChannelId(), count);
        }
        return count;
    }

    private int ingestFeishuMessages(ProjectChannel pc, List<FeishuClient.FeishuMessage> items) {
        List<Message> messages = new ArrayList<>();
        for (FeishuClient.FeishuMessage fm : items) {
            Message m = new Message();
            m.setProjectId(pc.getProjectId());
            m.setChannelType(Message.ChannelType.FEISHU);
            m.setChannelId(pc.getChannelId());
            m.setMsgId(fm.messageId());
            m.setSenderId(fm.senderId());
            m.setSenderName(fm.senderId() == null ? "未知" : feishuClient.getUserName(fm.senderId()));
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

    /** 企微渠道同步（会话存档，未接入 SDK 时抛出指引异常由上层降级日志） */
    public int syncWecomChannel(ProjectChannel pc) {
        return weComClient.syncArchive(pc.getChannelId(), raw -> {
            // SDK 接入后：raw 为解密后的聊天消息，转换为 Message 入库
        });
    }

    // ---------- 事件回调入库 ----------

    /** 飞书事件：im.message.receive_v1 */
    public void handleFeishuEvent(JsonNode event) {
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
        channelRepository.findByChannelTypeAndSyncEnabledTrue(ProjectChannel.ChannelType.FEISHU)
                .stream()
                .filter(pc -> pc.getChannelId().equals(chatId))
                .findFirst()
                .ifPresent(pc -> {
                    Message m = new Message();
                    m.setProjectId(pc.getProjectId());
                    m.setChannelType(Message.ChannelType.FEISHU);
                    m.setChannelId(chatId);
                    m.setMsgId(messageId);
                    String senderId = msg.path("sender").path("id").asText(null);
                    m.setSenderId(senderId);
                    m.setSenderName(senderId == null ? "未知" : feishuClient.getUserName(senderId));
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

    /** 企微回调消息（@机器人文本消息） */
    public void handleWecomCallback(String plainXml) {
        try {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            org.w3c.dom.Document doc = factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(
                    plainXml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            String chatId = textOf(doc, "ChatId");
            String content = textOf(doc, "Content");
            String msgId = textOf(doc, "MsgId");
            String fromUser = textOf(doc, "FromUserName");
            if (chatId.isBlank() || content.isBlank()) {
                return;
            }
            channelRepository.findByChannelTypeAndSyncEnabledTrue(ProjectChannel.ChannelType.WECOM)
                    .stream()
                    .filter(pc -> pc.getChannelId().equals(chatId))
                    .findFirst()
                    .ifPresent(pc -> {
                        Message m = new Message();
                        m.setProjectId(pc.getProjectId());
                        m.setChannelType(Message.ChannelType.WECOM);
                        m.setChannelId(chatId);
                        m.setMsgId(msgId.isBlank() ? "wecom_" + System.currentTimeMillis() : msgId);
                        m.setSenderId(fromUser);
                        m.setSenderName(fromUser);
                        m.setContent(content);
                        m.setMsgType(Message.MsgType.TEXT);
                        m.setSentAt(LocalDateTime.now());
                        m.setSource("EVENT");
                        if (messageService.save(m)) {
                            analysisService.analyzeAsync(pc.getProjectId(), "EVENT");
                        }
                    });
        } catch (Exception e) {
            log.warn("企微回调处理失败: {}", e.getMessage());
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
