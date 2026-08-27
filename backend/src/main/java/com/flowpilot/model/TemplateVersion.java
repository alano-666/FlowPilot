package com.flowpilot.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 模板版本快照（PRD 3.1.2 模板版本管理）。
 */
@Entity
@Table(name = "template_versions", indexes = @Index(name = "idx_tv_template", columnList = "templateId"))
public class TemplateVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long templateId;

    @Column(nullable = false)
    private int version;

    /** 全量快照 JSON：{name,description,nodes,branches,glossary} */
    @Column(columnDefinition = "TEXT")
    private String snapshotJson;

    @Column(length = 256)
    private String note;

    @Column(length = 64)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
