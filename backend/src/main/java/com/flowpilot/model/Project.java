package com.flowpilot.model;

import com.flowpilot.common.EncryptedStringConverter;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * AI 事件/项目实例（PRD 3.7）。一个项目 = 一条业务流程的独立执行实例。
 */
@Entity
@Table(name = "projects", indexes = {
        @Index(name = "idx_pj_status", columnList = "status"),
        @Index(name = "idx_pj_template", columnList = "templateId"),
        @Index(name = "idx_pj_owner", columnList = "ownerId")
})
public class Project {

    public enum Status { ACTIVE, PAUSED, ARCHIVED }

    public enum RiskStatus { NORMAL, WARNING, BLOCKED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 项目编号（如 P20260827001） */
    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false)
    private Long templateId;

    @Column(length = 128)
    private String templateName;

    /** 创建时固化的模板快照 JSON，模板后续修改不影响本项目 */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String templateSnapshotJson;

    @Column(length = 128)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.ACTIVE;

    /** 当前所处节点 key */
    @Column(length = 64)
    private String currentNodeKey;

    /** 进度百分比 0~1 */
    @Column(nullable = false)
    private double progress = 0.0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RiskStatus riskStatus = RiskStatus.NORMAL;

    /** 人工修正锁定：true 时 AI 不得自动覆盖（PRD 3.6.3） */
    @Column(nullable = false)
    private boolean manualLock = false;

    /** 项目开始时间 */
    private LocalDateTime startedAt;

    /** 最近动态（最新聊天摘要，AI 更新） */
    @Column(length = 512)
    private String latestActivity;

    @Column(nullable = false)
    private LocalDateTime lastActivityAt = LocalDateTime.now();

    /** 最近一次 AI 分析时间（增量水位线） */
    private LocalDateTime lastAnalyzedAt;

    @Column(nullable = false)
    private Long ownerId;

    @Column(length = 64)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }
    public String getTemplateSnapshotJson() { return templateSnapshotJson; }
    public void setTemplateSnapshotJson(String templateSnapshotJson) { this.templateSnapshotJson = templateSnapshotJson; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getCurrentNodeKey() { return currentNodeKey; }
    public void setCurrentNodeKey(String currentNodeKey) { this.currentNodeKey = currentNodeKey; }
    public double getProgress() { return progress; }
    public void setProgress(double progress) { this.progress = progress; }
    public RiskStatus getRiskStatus() { return riskStatus; }
    public void setRiskStatus(RiskStatus riskStatus) { this.riskStatus = riskStatus; }
    public boolean isManualLock() { return manualLock; }
    public void setManualLock(boolean manualLock) { this.manualLock = manualLock; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public String getLatestActivity() { return latestActivity; }
    public void setLatestActivity(String latestActivity) { this.latestActivity = latestActivity; }
    public LocalDateTime getLastActivityAt() { return lastActivityAt; }
    public void setLastActivityAt(LocalDateTime lastActivityAt) { this.lastActivityAt = lastActivityAt; }
    public LocalDateTime getLastAnalyzedAt() { return lastAnalyzedAt; }
    public void setLastAnalyzedAt(LocalDateTime lastAnalyzedAt) { this.lastAnalyzedAt = lastAnalyzedAt; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
