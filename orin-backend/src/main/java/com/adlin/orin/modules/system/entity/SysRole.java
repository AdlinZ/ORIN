package com.adlin.orin.modules.system.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 系统角色实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sys_role")
public class SysRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long roleId;

    /**
     * 角色代码: ROLE_ADMIN, ROLE_USER
     */
    @Column(unique = true, nullable = false, length = 50)
    private String roleCode;

    /**
     * 角色名称
     */
    @Column(nullable = false, length = 100)
    private String roleName;

    /**
     * 角色描述
     */
    @Column(length = 255)
    private String description;

    /**
     * 创建时间
     */
    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
