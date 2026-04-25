package com.adlin.orin.modules.collaboration.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 协作编舞模式配置
 *
 * 控制协作任务的编舞方式：
 * - JAVA_NATIVE: 使用 Java 内存编舞（原有方式）
 * - LANGGRAPH_MQ: 使用 LangGraph + RabbitMQ 编舞（新版方式）
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "orin.collaboration")
public class CollaborationOrchestrationMode {

    /**
     * 编舞模式：JAVA_NATIVE 或 LANGGRAPH_MQ
     */
    private String mode = "LANGGRAPH_MQ";

    /**
     * PARALLEL 模式是否启用 MQ（Week 1 默认 true）
     */
    private boolean mqForParallel = true;

    /**
     * SEQUENTIAL 模式是否启用 MQ（Week 2 默认 true）
     */
    private boolean mqForSequential = true;

    /**
     * CONSENSUS 模式是否启用 MQ（Week 2 默认 true）
     */
    private boolean mqForConsensus = true;

    /**
     * 是否使用 LANGGRAPH_MQ 模式
     */
    public boolean isLangGraphMqMode() {
        return "LANGGRAPH_MQ".equalsIgnoreCase(mode);
    }

    /**
     * 是否使用 Java 原生模式
     */
    public boolean isJavaNativeMode() {
        return "JAVA_NATIVE".equalsIgnoreCase(mode) || mode == null || mode.isEmpty();
    }

    /**
     * 判断指定协作模式是否启用 MQ
     */
    public boolean isMqEnabled(String collaborationMode) {
        // 只有在 LANGGRAPH_MQ 模式下才考虑 MQ
        if (!isLangGraphMqMode()) {
            return false;
        }

        if (collaborationMode == null) {
            return false;
        }

        String upper = collaborationMode.toUpperCase();
        return switch (upper) {
            case "PARALLEL" -> mqForParallel;
            case "SEQUENTIAL" -> mqForSequential;
            case "CONSENSUS" -> mqForConsensus;
            default -> false;
        };
    }

    /**
     * 获取当前模式描述
     */
    public String getModeDescription() {
        if (isJavaNativeMode()) {
            return "Java Native Orchestration (in-memory)";
        }
        return "LangGraph + MQ Orchestration (distributed)";
    }
}
