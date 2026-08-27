package com.flowpilot.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 通知任务（PRD 5.3 notification_jobs 表）。
 */
@Entity
@Table(name = "notification_jobs", indexes = {
        @Index(name = "idx_nj_project", columnList = "projectId"),
        @Index(name = "idx_nj_status", columnList = "status")
})
public class NotificationJob {

    public enum Type {
        /** 节点超时预警 */
        SLA_OVERDUE,
        /** 节点完成联动通知 */
        NODE_COMPLETED,
        /** 每日进度摘要 */
        DAILY_DIGEST,
        /** 沟通/进度风险预警 */
        RISK_ALERT
    }

    public enum Status { PENDING, SENT, FAILED, SKIPPED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Type type;

    @Column(length = 256)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    /** 通知目标 JSON：[{name, contact_type, contact_id}] */
    @Column(columnDefinition = "TEXT")
    private String targetsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(length = 512)
    private String errorMsg;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime executedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getTargetsJson() { return targetsJson; }
    public void setTargetsJson(String targetsJson) { this.targetsJson = targetsJson; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }
}
