package com.flowpilot.model;

import com.flowpilot.common.EncryptedStringConverter;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 聊天消息（PRD 5.3 messages 表）。按 (channelType, channelId, msgId) 唯一去重。
 */
@Entity
@Table(name = "messages", indexes = {
        @Index(name = "idx_msg_project_time", columnList = "projectId, sentAt"),
        @Index(name = "idx_msg_dedupe", columnList = "channelType, channelId, msgId", unique = true),
        @Index(name = "idx_msg_sentat", columnList = "sentAt")
})
public class Message {

    public enum ChannelType { FEISHU, WECOM, WECHAT_IMPORT, EMAIL, MOCK }

    public enum MsgType { TEXT, IMAGE, FILE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ChannelType channelType;

    @Column(nullable = false, length = 128)
    private String channelId;

    /** 渠道内消息 ID（去重键） */
    @Column(nullable = false, length = 128)
    private String msgId;

    @Column(length = 128)
    private String senderId;

    @Column(length = 64)
    private String senderName;

    /** 消息内容（配置 FLOWPILOT_DATA_ENCRYPTION_KEY 后数据库加密存储，docs/09） */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MsgType msgType = MsgType.TEXT;

    @Column(nullable = false)
    private LocalDateTime sentAt;

    /** 原始报文 JSON（合规审计用，到期自动清理；加密存储） */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String rawJson;

    /** 数据来源：SYNC(渠道同步) / EVENT(事件回调) / IMPORT(文件导入) / MOCK */
    @Column(length = 16)
    private String source;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public ChannelType getChannelType() { return channelType; }
    public void setChannelType(ChannelType channelType) { this.channelType = channelType; }
    public String getChannelId() { return channelId; }
    public void setChannelId(String channelId) { this.channelId = channelId; }
    public String getMsgId() { return msgId; }
    public void setMsgId(String msgId) { this.msgId = msgId; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public MsgType getMsgType() { return msgType; }
    public void setMsgType(MsgType msgType) { this.msgType = msgType; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
    public String getRawJson() { return rawJson; }
    public void setRawJson(String rawJson) { this.rawJson = rawJson; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
