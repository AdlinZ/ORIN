package com.adlin.orin.modules.knowledge.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 关键词标签DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KeywordTag {

    /**
     * 标签名称
     */
    private String label;

    /**
     * 权重/热度 (用于词云大小)
     */
    private Double weight;

    /**
     * 透明度 (用于词云展示效果)
     */
    private Double opacity;

    /**
     * 字体大小 (用于词云展示效果)
     */
    private Integer size;

    /**
     * 颜色
     */
    private String color;

    /**
     * 动画延迟 (秒)
     */
    private Double delay;

    /**
     * 知识库ID (可选)
     */
    private String datasetId;
}
