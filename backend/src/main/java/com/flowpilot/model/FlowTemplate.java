package com.flowpilot.model;

import com.flowpilot.common.EncryptedStringConverter;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 流程模板（PRD 3.1 流程知识库）。节点/分支/词库以 JSON 存储，
 * 结构化定义见 docs/04-数据库设计.md 与 PRD 3.1.3 示例。
 */
@Entity
@Table(name = "flow_templates", indexes = {
        @Index(name = "idx_ft_name", columnList = "name"),
        @Index(name = "idx_ft_status", columnList = "status")
})
public class FlowTemplate {

    public enum Status {
        /** 草稿（AI 解析后待确认） */
        DRAFT,
        /** 已发布（可被项目引用） */
        ACTIVE,
        /** 已停用 */
        ARCHIVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 512)
    private String description;

    @Column(nullable = false)
    private int version = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.DRAFT;

    /** 节点数组 JSON：[{key,name,type,completion_criteria,responsible_roles,sla_hours,order_no}] */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String nodesJson;

    /** 分支数组 JSON：[{condition,from,to}] */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String branchesJson;

    /** 词库数组 JSON：[{term,synonyms,explanation}] */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "TEXT")
    private String glossaryJson;

    /** 来源文档名（如：远程安装设备操作流程V2.0.docx） */
    @Column(length = 256)
    private String sourceDocName;

    /** 文档提取文本预览（仅响应展示用，不入库），供前端判断文件解析是否完整 */
    @Transient
    private String extractedTextPreview;

    @Column(length = 64)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getNodesJson() { return nodesJson; }
    public void setNodesJson(String nodesJson) { this.nodesJson = nodesJson; }
    public String getBranchesJson() { return branchesJson; }
    public void setBranchesJson(String branchesJson) { this.branchesJson = branchesJson; }
    public String getGlossaryJson() { return glossaryJson; }
    public void setGlossaryJson(String glossaryJson) { this.glossaryJson = glossaryJson; }
    public String getSourceDocName() { return sourceDocName; }
    public void setSourceDocName(String sourceDocName) { this.sourceDocName = sourceDocName; }
    public String getExtractedTextPreview() { return extractedTextPreview; }
    public void setExtractedTextPreview(String extractedTextPreview) { this.extractedTextPreview = extractedTextPreview; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
