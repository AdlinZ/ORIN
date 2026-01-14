package com.adlin.orin.modules.knowledge.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库绑定信息 (DTO/Entity Hybrid)
 * 这里不做持久化，每次通过 API 实时查询或 Mock
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentKnowledge {
    private String id;
    private String name;
    private String status; // ENABLED, DISABLED
    private Integer docCount;
    private Double totalSizeMb;
}
