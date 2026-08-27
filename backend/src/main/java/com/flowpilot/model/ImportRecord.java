package com.flowpilot.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 微信导入记录（PRD 3.2 微信个人版手动/半自动导入）。
 */
@Entity
@Table(name = "import_records", indexes = @Index(name = "idx_ir_project", columnList = "projectId"))
public class ImportRecord {

    public enum Format { TXT, CSV, IMAGE }

    public enum Status { SUCCESS, PARTIAL, FAILED }

    public enum Source {
        /** 前端上传 */
        API,
        /** 文件夹监控自动导入 */
        WATCH
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 256)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Format format;

    private int messageCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.SUCCESS;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Source source = Source.API;

    @Column(length = 512)
    private String note;

    @Column(length = 64)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Format getFormat() { return format; }
    public void setFormat(Format format) { this.format = format; }
    public int getMessageCount() { return messageCount; }
    public void setMessageCount(int messageCount) { this.messageCount = messageCount; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Source getSource() { return source; }
    public void setSource(Source source) { this.source = source; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
