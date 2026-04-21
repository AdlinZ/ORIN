package com.adlin.orin.modules.conversation.controller;

import com.adlin.orin.modules.conversation.dto.tooling.ToolBindingDto;
import com.adlin.orin.modules.conversation.dto.tooling.ToolCatalogItemDto;
import com.adlin.orin.modules.conversation.dto.tooling.ToolCatalogUpdateRequest;
import com.adlin.orin.modules.conversation.entity.AgentChatSession;
import com.adlin.orin.modules.conversation.repository.AgentChatSessionRepository;
import com.adlin.orin.modules.conversation.tooling.ToolBindingService;
import com.adlin.orin.modules.conversation.tooling.ToolCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agent-tools")
@RequiredArgsConstructor
@Tag(name = "Agent Tooling", description = "统一工具目录与绑定管理")
public class AgentToolController {

    private final ToolCatalogService toolCatalogService;
    private final ToolBindingService toolBindingService;
    private final AgentChatSessionRepository agentChatSessionRepository;

    @GetMapping("/catalog")
    @Operation(summary = "获取统一工具目录")
    public ResponseEntity<List<ToolCatalogItemDto>> getCatalog(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "true") boolean includeDisabled) {
        return ResponseEntity.ok(toolCatalogService.listCatalog(category, includeDisabled));
    }

    @PutMapping("/catalog/{toolId}")
    @Operation(summary = "更新工具目录项")
    public ResponseEntity<ToolCatalogItemDto> updateCatalogItem(
            @PathVariable String toolId,
            @RequestBody ToolCatalogUpdateRequest request) {
        return ResponseEntity.ok(toolCatalogService.upsertCatalogItem(toolId, request));
    }

    @GetMapping("/bindings/agents/{agentId}")
    @Operation(summary = "获取 Agent 工具绑定")
    public ResponseEntity<ToolBindingDto> getAgentBinding(@PathVariable String agentId) {
        return ResponseEntity.ok(toolBindingService.getAgentBinding(agentId));
    }

    @PutMapping("/bindings/agents/{agentId}")
    @Operation(summary = "保存 Agent 工具绑定")
    public ResponseEntity<ToolBindingDto> saveAgentBinding(
            @PathVariable String agentId,
            @RequestBody ToolBindingDto request) {
        return ResponseEntity.ok(toolBindingService.saveAgentBinding(agentId, request));
    }

    @GetMapping("/bindings/sessions/{sessionId}")
    @Operation(summary = "获取 Session 工具绑定")
    public ResponseEntity<ToolBindingDto> getSessionBinding(@PathVariable String sessionId) {
        AgentChatSession session = agentChatSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));
        return ResponseEntity.ok(toolBindingService.getSessionBinding(sessionId, session.getAgentId()));
    }

    @PutMapping("/bindings/sessions/{sessionId}")
    @Operation(summary = "保存 Session 工具绑定")
    public ResponseEntity<ToolBindingDto> saveSessionBinding(
            @PathVariable String sessionId,
            @RequestBody ToolBindingDto request) {
        AgentChatSession session = agentChatSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));
        return ResponseEntity.ok(toolBindingService.saveSessionBinding(sessionId, session.getAgentId(), request));
    }
}
