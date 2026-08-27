package com.flowpilot.model;

import com.flowpilot.common.EncryptedStringConverter;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * AI 证据链（PRD 5.3 ai_insights 表）：每条消息对某节点的支撑证据。
 */
@Entity
@Table(name = "ai_insights", indexes = {
        @Index(name = "idx_ai_project", columnList = "projectId"),
        @Index(name = "idx_ai_run", columnList = "runId")
})
public class AiInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long runId;

    @Column(nullable = false)
    private Long messageId;

    /** 证据支撑的节点 key */
    @Column(length = 64)
    private String detectedNodeKey;

    /** 证据摘要（如：客户IT回复策略已生效） */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 512)
    private String summary;

    @Column(nullable = false)
    private double confidence = 0.0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
    public String getDetectedNodeKey() { return detectedNodeKey; }
    public void setDetectedNodeKey(String detectedNodeKey) { this.detectedNodeKey = detectedNodeKey; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
