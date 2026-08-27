package com.flowpilot.model;

import com.flowpilot.common.EncryptedStringConverter;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * AI 待确认建议（PRD 3.6.3 核心规则）：
 * 项目被人工锁定时，AI 分析结果不落库，转为待用户确认的建议。
 */
@Entity
@Table(name = "pending_suggestions", indexes = {
        @Index(name = "idx_ps_project", columnList = "projectId"),
        @Index(name = "idx_ps_status", columnList = "status")
})
public class PendingSuggestion {

    public enum Status { PENDING, CONFIRMED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long runId;

    /** 建议内容 JSON（与 AI 分析结果同构） */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String suggestionJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(length = 64)
    private String handledBy;

    private LocalDateTime handledAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public String getSuggestionJson() { return suggestionJson; }
    public void setSuggestionJson(String suggestionJson) { this.suggestionJson = suggestionJson; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getHandledBy() { return handledBy; }
    public void setHandledBy(String handledBy) { this.handledBy = handledBy; }
    public LocalDateTime getHandledAt() { return handledAt; }
    public void setHandledAt(LocalDateTime handledAt) { this.handledAt = handledAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
