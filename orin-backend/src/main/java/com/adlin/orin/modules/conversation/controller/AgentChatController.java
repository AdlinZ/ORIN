package com.adlin.orin.modules.conversation.controller;

import com.adlin.orin.modules.conversation.dto.*;
import com.adlin.orin.modules.conversation.service.AgentChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/agents/chat")
@RequiredArgsConstructor
@Tag(name = "智能体对话", description = "智能体工作台 - 支持知识库附加的对话接口")
public class AgentChatController {

    private final AgentChatService agentChatService;

    @Operation(summary = "创建会话")
    @PostMapping("/sessions")
    public ResponseEntity<SessionResponse> createSession(@RequestBody CreateSessionRequest request) {
        return ResponseEntity.ok(agentChatService.createSession(request));
    }

    @Operation(summary = "获取会话列表")
    @GetMapping("/sessions")
    public ResponseEntity<List<SessionResponse>> listSessions(
            @RequestParam(required = false) String agentId) {
        return ResponseEntity.ok(agentChatService.listSessions(agentId));
    }

    @Operation(summary = "获取会话详情")
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSession(@PathVariable String sessionId) {
        return ResponseEntity.ok(agentChatService.getSession(sessionId));
    }

    @Operation(summary = "发送消息")
    @PostMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @PathVariable String sessionId,
            @RequestBody ChatMessageRequest request) {
        return ResponseEntity.ok(agentChatService.sendMessage(sessionId, request));
    }

    @Operation(summary = "附加知识库到会话")
    @PostMapping("/sessions/{sessionId}/attach-kb")
    public ResponseEntity<Map<String, Object>> attachKnowledgeBase(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> body) {
        agentChatService.attachKnowledgeBase(sessionId, body.get("kbId"));
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "解绑知识库")
    @PostMapping("/sessions/{sessionId}/detach-kb")
    public ResponseEntity<Map<String, Object>> detachKnowledgeBase(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> body) {
        agentChatService.detachKnowledgeBase(sessionId, body.get("kbId"));
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "获取会话附加的知识库")
    @GetMapping("/sessions/{sessionId}/kbs")
    public ResponseEntity<List<String>> getAttachedKnowledgeBases(@PathVariable String sessionId) {
        return ResponseEntity.ok(agentChatService.getAttachedKnowledgeBases(sessionId));
    }

    @Operation(summary = "删除会话")
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> deleteSession(@PathVariable String sessionId) {
        agentChatService.deleteSession(sessionId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return ResponseEntity.ok(result);
    }
}