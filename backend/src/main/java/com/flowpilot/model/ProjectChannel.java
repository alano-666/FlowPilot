package com.flowpilot.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 项目绑定的沟通渠道（PRD 3.2.1 单项目可绑定多渠道）。
 */
@Entity
@Table(name = "project_channels", indexes = {
        @Index(name = "idx_pc_project", columnList = "projectId"),
        @Index(name = "idx_pc_channel", columnList = "channelType, channelId")
})
public class ProjectChannel {

    public enum ChannelType {
        /** 飞书群聊 */
        FEISHU,
        /** 企业微信群聊 */
        WECOM,
        /** 微信个人版导入（文件/截图） */
        WECHAT_IMPORT,
        /** 演示渠道（无凭证联调用） */
        MOCK
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ChannelType channelType;

    /** 渠道内唯一标识：飞书 chat_id / 企微群 id / 微信导入批次名 / mock 标识 */
    @Column(nullable = false, length = 128)
    private String channelId;

    @Column(length = 128)
    private String channelName;

    @Column(nullable = false)
    private boolean syncEnabled = true;

    private LocalDateTime lastSyncAt;

    /** 飞书消息分页游标等 */
    @Column(length = 256)
    private String lastSyncCursor;

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
    public String getChannelName() { return channelName; }
    public void setChannelName(String channelName) { this.channelName = channelName; }
    public boolean isSyncEnabled() { return syncEnabled; }
    public void setSyncEnabled(boolean syncEnabled) { this.syncEnabled = syncEnabled; }
    public LocalDateTime getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(LocalDateTime lastSyncAt) { this.lastSyncAt = lastSyncAt; }
    public String getLastSyncCursor() { return lastSyncCursor; }
    public void setLastSyncCursor(String lastSyncCursor) { this.lastSyncCursor = lastSyncCursor; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
