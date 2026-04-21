package com.adlin.orin.modules.conversation.tooling;

import com.adlin.orin.modules.conversation.dto.ChatMessageResponse;
import com.adlin.orin.modules.conversation.dto.tooling.ToolCatalogItemDto;
import com.adlin.orin.modules.conversation.tool.ToolExecutionContext;
import com.adlin.orin.modules.skill.entity.McpService;
import com.adlin.orin.modules.skill.entity.SkillEntity;
import com.adlin.orin.modules.skill.repository.McpServiceRepository;
import com.adlin.orin.modules.skill.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

@Component
@RequiredArgsConstructor
public class ToolExecutor {

    private final ToolRegistry toolRegistry;
    private final ToolRouter toolRouter;
    private final ToolExecutionLogService toolExecutionLogService;
    private final SkillRepository skillRepository;
    private final McpServiceRepository mcpServiceRepository;

    public static final String SHARED_STATE_FUNCTION_CALL_TOOLS = "functionCallTools";

    public void applyBinding(ToolExecutionContext ctx, boolean modelSupportsFunctionCalling,
            BiConsumer<String, Object> eventPublisher) {
        if (ctx == null || ctx.getToolIds() == null || ctx.getToolIds().isEmpty()) {
            return;
        }

        List<ToolCatalogItemDto> tools = toolRegistry.resolveByToolIds(ctx.getToolIds());
        List<String> contextOnly = new ArrayList<>();
        List<ToolCatalogItemDto> functionCallTools = new ArrayList<>();

        for (ToolCatalogItemDto tool : tools) {
            long started = System.currentTimeMillis();
            String routedMode = toolRouter.routeRuntimeMode(tool, modelSupportsFunctionCalling);
            boolean success = true;
            String status = "success";
            String message = "工具已纳入上下文";
            String errorCode = null;
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("category", tool.getCategory());
            detail.put("source", tool.getSource());
            detail.put("requestedRuntimeMode", tool.getRuntimeMode());
            detail.put("routedRuntimeMode", routedMode);

            if (!Boolean.TRUE.equals(tool.getEnabled())) {
                success = false;
                status = "warning";
                errorCode = "TOOL_DISABLED";
                message = "工具已禁用，自动跳过";
            } else if (tool.getToolId().startsWith("mcp:")) {
                Long mcpId = parseLong(tool.getToolId().substring("mcp:".length()));
                Optional<McpService> serviceOpt = mcpId != null ? mcpServiceRepository.findById(mcpId) : Optional.empty();
                if (serviceOpt.isEmpty()) {
                    success = false;
                    status = "error";
                    errorCode = "MCP_NOT_FOUND";
                    message = "MCP 服务不存在，降级为 context_only";
                } else {
                    McpService svc = serviceOpt.get();
                    detail.put("mcpStatus", svc.getStatus() != null ? svc.getStatus().name() : "UNKNOWN");
                    detail.put("mcpEnabled", svc.getEnabled());
                    if (ToolCatalogService.MODE_FUNCTION_CALL.equalsIgnoreCase(routedMode)
                            && svc.getStatus() != McpService.McpStatus.CONNECTED) {
                        success = false;
                        status = "warning";
                        errorCode = "MCP_DEGRADED";
                        message = "MCP 不可执行，降级为 context_only";
                        routedMode = ToolCatalogService.MODE_CONTEXT_ONLY;
                        detail.put("degradeReason", "service_not_connected");
                    }
                }
            } else if (tool.getToolId().startsWith("skill:")) {
                Long skillId = parseLong(tool.getToolId().substring("skill:".length()));
                Optional<SkillEntity> skillOpt = skillId != null ? skillRepository.findById(skillId) : Optional.empty();
                if (skillOpt.isEmpty()) {
                    success = false;
                    status = "error";
                    errorCode = "SKILL_NOT_FOUND";
                    message = "Skill 不存在，自动跳过";
                } else {
                    SkillEntity skill = skillOpt.get();
                    detail.put("skillStatus", skill.getStatus() != null ? skill.getStatus().name() : "UNKNOWN");
                    if (skill.getStatus() != SkillEntity.SkillStatus.ACTIVE) {
                        success = false;
                        status = "warning";
                        errorCode = "SKILL_INACTIVE";
                        message = "Skill 非 ACTIVE，降级为 context_only";
                        routedMode = ToolCatalogService.MODE_CONTEXT_ONLY;
                    }
                }
            }

            if (ToolCatalogService.MODE_CONTEXT_ONLY.equalsIgnoreCase(routedMode)) {
                contextOnly.add(tool.getDisplayName() + "(" + tool.getToolId() + ")");
            } else if (success
                    && ToolCatalogService.MODE_FUNCTION_CALL.equalsIgnoreCase(routedMode)) {
                functionCallTools.add(tool);
            }

            long duration = Math.max(1, System.currentTimeMillis() - started);
            ChatMessageResponse.ToolTrace trace = ChatMessageResponse.ToolTrace.builder()
                    .type("TOOL_BIND")
                    .kbId(tool.getToolId())
                    .message(message)
                    .status(status)
                    .durationMs(duration)
                    .detail(detail)
                    .build();
            ctx.addTrace(trace);
            if (eventPublisher != null) {
                eventPublisher.accept("trace", trace);
            }

            toolExecutionLogService.log(
                    ctx.getSessionId(),
                    ctx.getAgentId(),
                    tool.getToolId(),
                    routedMode,
                    success,
                    errorCode,
                    duration,
                    detail);
        }

        if (!contextOnly.isEmpty()) {
            String existing = (String) ctx.getSharedState("toolContext");
            String prefix = existing == null || existing.isBlank() ? "" : existing + "\n";
            ctx.putSharedState("toolContext", prefix + "可用工具上下文: " + String.join(", ", contextOnly));
        }

        if (!functionCallTools.isEmpty()) {
            ctx.putSharedState(SHARED_STATE_FUNCTION_CALL_TOOLS, functionCallTools);
        }
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception ignore) {
            return null;
        }
    }
}
