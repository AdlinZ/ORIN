package com.adlin.orin.modules.knowledge.entity;

/**
 * 图谱构建状态枚举
 */
public enum GraphBuildState {
    /**
     * 等待构建
     */
    PENDING("待构建"),

    /**
     * 实体抽取中
     */
    ENTITY_EXTRACTING("实体抽取中"),

    /**
     * 关系抽取中
     */
    RELATION_EXTRACTING("关系抽取中"),

    /**
     * 构建成功
     */
    SUCCESS("构建成功"),

    /**
     * 构建失败
     */
    FAILED("构建失败"),

    /**
     * 构建中（总状态）
     */
    BUILDING("构建中");

    private final String description;

    GraphBuildState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
