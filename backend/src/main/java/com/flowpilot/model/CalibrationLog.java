package com.flowpilot.model;

import com.flowpilot.common.EncryptedStringConverter;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 人工校准日志（PRD 3.6.3）：记录修改人、时间、前后值对比，满足审计需求。
 */
@Entity
@Table(name = "calibration_logs", indexes = @Index(name = "idx_cl_project", columnList = "projectId, createdAt"))
public class CalibrationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 64)
    private String username;

    /** 被修改字段：current_node / progress / risk_status / stakeholder / template */
    @Column(length = 64)
    private String field;

    /** 修改前值（JSON 或字符串） */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String oldValue;

    /** 修改后值 */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String newValue;

    @Column(length = 512)
    private String note;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getField() { return field; }
    public void setField(String field) { this.field = field; }
    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }
    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
