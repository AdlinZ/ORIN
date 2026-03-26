package com.adlin.orin.common.enums;

/**
 * 统一任务状态枚举
 * 收敛 TaskEntity.TaskStatus 和 TaskQueue.status 的定义
 */
public enum TaskStatus {
    // 通用状态
    PENDING("待执行"),
    QUEUED("排队中"),
    RUNNING("执行中"),
    RETRYING("重试中"),
    COMPLETED("已完成"),
    FAILED("失败"),
    DEAD("死信"),
    CANCELLED("已取消");

    private final String description;

    TaskStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 从字符串转换（兼容旧数据）
     */
    public static TaskStatus fromString(String status) {
        if (status == null) {
            return PENDING;
        }
        try {
            return valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PENDING;
        }
    }
}
