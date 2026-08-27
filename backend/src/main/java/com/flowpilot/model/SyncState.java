package com.flowpilot.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 渠道同步水位线（增量同步游标）。
 */
@Entity
@Table(name = "sync_states", indexes = @Index(name = "idx_ss_channel", columnList = "channelType, channelId", unique = true))
public class SyncState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 24)
    private String channelType;

    @Column(nullable = false, length = 128)
    private String channelId;

    private LocalDateTime lastSyncAt;

    /** 分页游标（飞书 page_token 等） */
    @Column(length = 256)
    private String lastCursor;

    /** 最后同步到的消息 ID */
    @Column(length = 128)
    private String lastMessageId;

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getChannelType() { return channelType; }
    public void setChannelType(String channelType) { this.channelType = channelType; }
    public String getChannelId() { return channelId; }
    public void setChannelId(String channelId) { this.channelId = channelId; }
    public LocalDateTime getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(LocalDateTime lastSyncAt) { this.lastSyncAt = lastSyncAt; }
    public String getLastCursor() { return lastCursor; }
    public void setLastCursor(String lastCursor) { this.lastCursor = lastCursor; }
    public String getLastMessageId() { return lastMessageId; }
    public void setLastMessageId(String lastMessageId) { this.lastMessageId = lastMessageId; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
