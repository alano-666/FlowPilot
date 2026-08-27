package com.flowpilot.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.flowpilot.common.EncryptedStringConverter;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 系统用户（PRD 2：流程负责人/一线执行/企业管理者）。
 */
@Entity
@Table(name = "users", indexes = @Index(name = "idx_users_username", columnList = "username", unique = true))
public class User {

    /**
     * 角色层级（声明顺序即权限高低，ordinal 越大权限越大）：
     * ADMIN(企业管理者) > MANAGER(流程负责人) > MEMBER(一线执行)
     */
    public enum Role {
        MEMBER,
        MANAGER,
        ADMIN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @JsonIgnore
    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(length = 64)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role = Role.MEMBER;

    /** 飞书 open_id，用于一键唤起会话与@提醒 */
    @Column(length = 64)
    private String feishuOpenId;

    /** 企微 userid */
    @Column(length = 64)
    private String wecomUserId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 64)
    private String phone;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public String getFeishuOpenId() { return feishuOpenId; }
    public void setFeishuOpenId(String feishuOpenId) { this.feishuOpenId = feishuOpenId; }
    public String getWecomUserId() { return wecomUserId; }
    public void setWecomUserId(String wecomUserId) { this.wecomUserId = wecomUserId; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
