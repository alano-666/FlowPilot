package com.flowpilot.model;

import com.flowpilot.common.EncryptedStringConverter;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 一次 AI 分析运行记录（PRD 3.3.2 数据落库）。resultJson 为结构化识别结果全文。
 */
@Entity
@Table(name = "analysis_runs", indexes = {
        @Index(name = "idx_ar_project", columnList = "projectId, createdAt"),
        @Index(name = "idx_ar_status", columnList = "status")
})
public class AnalysisRun {

    public enum TriggerType { MANUAL, SCHEDULE, EVENT }

    public enum Status { RUNNING, SUCCESS, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(length = 32)
    private String provider;

    @Column(length = 64)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TriggerType triggerType = TriggerType.MANUAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.RUNNING;

    /** AI 结构化识别结果 JSON（含 evidence/risks/suggested_next_action 等） */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String resultJson;

    @Column(length = 512)
    private String errorMsg;

    /** 本次分析消费的消息条数 */
    private int messageCount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime finishedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public TriggerType getTriggerType() { return triggerType; }
    public void setTriggerType(TriggerType triggerType) { this.triggerType = triggerType; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    public int getMessageCount() { return messageCount; }
    public void setMessageCount(int messageCount) { this.messageCount = messageCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
