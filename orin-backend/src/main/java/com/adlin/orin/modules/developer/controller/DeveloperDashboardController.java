package com.adlin.orin.modules.developer.controller;

import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.apikey.entity.ApiKey;
import com.adlin.orin.modules.apikey.repository.ApiKeyRepository;
import com.adlin.orin.modules.trace.service.TraceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 开发者工作台控制器
 *
 * 提供开发者专属的聚合数据视图：
 * - 我创建的智能体（按状态分组）
 * - 我的 API Key 统计
 * - 最近 Trace
 * - 快速入口
 */
@RestController
@RequestMapping("/api/v1/developer")
@RequiredArgsConstructor
@Tag(name = "Developer Dashboard", description = "开发者工作台")
public class DeveloperDashboardController {

    private final AgentMetadataRepository agentMetadataRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final TraceService traceService;

    @GetMapping("/summary")
    @Operation(summary = "获取开发者工作台汇总数据")
    public Map<String, Object> getDeveloperSummary() {
        Long userId = getCurrentUserId();
        Map<String, Object> summary = new LinkedHashMap<>();

        summary.put("myAgents", myAgents(userId));
        summary.put("myApiKeys", myApiKeyStats(userId));
        summary.put("recentTraces", traceService.getRecentTraceSummariesByUserId(userId, 10));
        summary.put("quickLinks", quickLinks());

        return summary;
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }
        try {
            return Long.parseLong(auth.getPrincipal().toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, Object> myAgents(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (userId == null) {
            result.put("total", 0);
            result.put("agents", List.of());
            return result;
        }
        List<AgentMetadata> agents = agentMetadataRepository.findByOwnerUserId(userId);
        result.put("total", agents.size());
        result.put("agents", agents.stream().map(a -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", a.getAgentId());
            item.put("name", a.getName());
            item.put("modelName", a.getModelName());
            item.put("providerType", a.getProviderType());
            item.put("mcpExposed", a.isMcpExposed());
            return item;
        }).collect(Collectors.toList()));
        return result;
    }

    private Map<String, Object> myApiKeyStats(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (userId == null) {
            result.put("total", 0);
            result.put("activeKeys", 0);
            result.put("keys", List.of());
            return result;
        }
        String uid = String.valueOf(userId);
        List<ApiKey> keys = apiKeyRepository.findByUserId(uid);
        int active = (int) keys.stream().filter(k -> Boolean.TRUE.equals(k.getEnabled())).count();
        result.put("total", keys.size());
        result.put("activeKeys", active);
        result.put("keys", keys.stream().map(k -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", k.getId());
            item.put("keyPrefix", k.getKeyPrefix());
            item.put("name", k.getName());
            item.put("enabled", k.getEnabled());
            item.put("rateLimitPerMinute", k.getRateLimitPerMinute());
            item.put("rateLimitPerDay", k.getRateLimitPerDay());
            item.put("monthlyTokenQuota", k.getMonthlyTokenQuota());
            item.put("usedTokens", k.getUsedTokens());
            item.put("lastUsedAt", k.getLastUsedAt());
            return item;
        }).collect(Collectors.toList()));
        return result;
    }

    private List<Map<String, String>> quickLinks() {
        return List.of(
                Map.of("title", "智能体工作台", "path", "/dashboard/applications/workspace", "icon", "ChatDotRound"),
                Map.of("title", "工作流中心", "path", "/dashboard/applications/workflows", "icon", "Edit"),
                Map.of("title", "API 文档", "path", "/dashboard/control/unified-gateway", "icon", "Document"),
                Map.of("title", "Playground", "path", "/dashboard/applications/playground", "icon", "Pointer")
        );
    }
}
