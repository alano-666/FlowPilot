package com.flowpilot.model;

import com.flowpilot.common.EncryptedStringConverter;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 项目干系人（PRD 3.3.3 stakeholders_update）：节点责任角色对应的具体人员。
 */
@Entity
@Table(name = "stakeholders", indexes = {
        @Index(name = "idx_st_project", columnList = "projectId"),
        @Index(name = "idx_st_node", columnList = "projectId, nodeKey")
})
public class Stakeholder {

    public enum ContactType {
        /** 飞书用户 */
        FEISHU,
        /** 企业微信用户 */
        WECOM,
        /** 微信个人 */
        WECHAT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(length = 64)
    private String nodeKey;

    /** 流程中角色（如 客户IT、我方技术支持） */
    @Column(length = 64)
    private String role;

    @Column(length = 64)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private ContactType contactType;

    /** open_id / userid / 微信号 */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 128)
    private String contactId;

    @Column(length = 512)
    private String avatarUrl;

    /** 微信个人版：展示二维码/复制微信号用 */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 64)
    private String wechatId;

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getNodeKey() { return nodeKey; }
    public void setNodeKey(String nodeKey) { this.nodeKey = nodeKey; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ContactType getContactType() { return contactType; }
    public void setContactType(ContactType contactType) { this.contactType = contactType; }
    public String getContactId() { return contactId; }
    public void setContactId(String contactId) { this.contactId = contactId; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getWechatId() { return wechatId; }
    public void setWechatId(String wechatId) { this.wechatId = wechatId; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
