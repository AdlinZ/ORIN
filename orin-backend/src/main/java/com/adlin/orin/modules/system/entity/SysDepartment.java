package com.adlin.orin.modules.system.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 部门实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sys_department")
public class SysDepartment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long departmentId;

    /**
     * 部门名称
     */
    @Column(nullable = false, length = 100)
    private String departmentName;

    /**
     * 部门编码（唯一）
     */
    @Column(unique = true, length = 50)
    private String departmentCode;

    /**
     * 父部门ID（顶级部门为0或null）
     */
    @Column(name = "parent_id")
    private Long parentId;

    /**
     * 排序号
     */
    @Column(name = "order_num")
    private Integer orderNum;

    /**
     * 部门状态：ENABLED, DISABLED
     */
    @Column(length = 20)
    private String status;

    /**
     * 部门负责人
     */
    @Column(length = 50)
    private String leader;

    /**
     * 联系电话
     */
    @Column(length = 20)
    private String phone;

    /**
     * 部门描述
     */
    @Column(length = 500)
    private String description;

    /**
     * 创建时间
     */
    @Column(name = "create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (status == null) {
            status = "ENABLED";
        }
        if (orderNum == null) {
            orderNum = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}