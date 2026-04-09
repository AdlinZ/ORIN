package com.adlin.orin.modules.collaboration.controller;

import com.adlin.orin.modules.collaboration.dto.CollabSessionDtos;
import com.adlin.orin.modules.collaboration.service.CollaborationSessionService;
import com.adlin.orin.modules.collaboration.service.CollaborationSessionStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/collaboration/sessions")
@RequiredArgsConstructor
@Tag(name = "Collaboration Session", description = "会话优先的多智能体协作 API")
public class CollaborationSessionController {

    private final CollaborationSessionService sessionService;
    private final CollaborationSessionStreamService streamService;

    @Operation(summary = "创建协作会话")
    @PostMapping
    public ResponseEntity<CollabSessionDtos.SessionView> createSession(
            @RequestBody(required = false) CollabSessionDtos.SessionCreateRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        CollabSessionDtos.SessionCreateRequest body = request != null
                ? request
                : CollabSessionDtos.SessionCreateRequest.builder().build();
        return ResponseEntity.ok(sessionService.createSession(body, userId));
    }

    @Operation(summary = "获取协作会话列表")
    @GetMapping
    public ResponseEntity<List<CollabSessionDtos.SessionView>> listSessions(
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId) {
        return ResponseEntity.ok(sessionService.listSessions(userId));
    }

    @Operation(summary = "提交用户消息并触发协作回合")
    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<CollabSessionDtos.TurnStartResponse> sendMessage(
            @PathVariable String sessionId,
            @RequestBody CollabSessionDtos.SessionMessageRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {

        String finalTraceId = traceId != null && !traceId.isBlank()
                ? traceId
                : UUID.randomUUID().toString().replace("-", "");
        return ResponseEntity.ok(sessionService.sendMessage(sessionId, request, userId, finalTraceId));
    }

    @Operation(summary = "获取协作会话消息历史")
    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<List<CollabSessionDtos.MessageView>> listMessages(
            @PathVariable String sessionId,
            @RequestParam(required = false) String turnId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return ResponseEntity.ok(sessionService.listMessages(sessionId, turnId, page, size));
    }

    @Operation(summary = "获取会话状态")
    @GetMapping("/{sessionId}/state")
    public ResponseEntity<CollabSessionDtos.SessionStateView> getState(
            @PathVariable String sessionId,
            @RequestParam(required = false) String turnId) {
        return ResponseEntity.ok(sessionService.getState(sessionId, turnId));
    }

    @Operation(summary = "SSE 流式获取协作回合事件")
    @GetMapping(value = "/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable String sessionId,
            @RequestParam String turnId) {
        SseEmitter emitter = streamService.register(sessionId, turnId);

        try {
            for (CollabSessionDtos.StreamEvent event : sessionService.replayEvents(sessionId, turnId)) {
                emitter.send(SseEmitter.event().name(event.getEventType()).data(event));
            }
        } catch (IOException e) {
            log.warn("Failed to replay stream events: sessionId={}, turnId={}", sessionId, turnId, e);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    @Operation(summary = "获取会话协作运营指标")
    @GetMapping("/metrics")
    public ResponseEntity<CollabSessionDtos.SessionMetricsView> getMetrics(
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(sessionService.getSessionMetrics(hours));
    }

    @Operation(summary = "暂停协作回合")
    @PostMapping("/{sessionId}/turns/{turnId}/pause")
    public ResponseEntity<CollabSessionDtos.ActionResponse> pauseTurn(
            @PathVariable String sessionId,
            @PathVariable String turnId) {
        return ResponseEntity.ok(sessionService.pauseTurn(sessionId, turnId));
    }

    @Operation(summary = "恢复协作回合")
    @PostMapping("/{sessionId}/turns/{turnId}/resume")
    public ResponseEntity<CollabSessionDtos.ActionResponse> resumeTurn(
            @PathVariable String sessionId,
            @PathVariable String turnId) {
        return ResponseEntity.ok(sessionService.resumeTurn(sessionId, turnId));
    }

    @Operation(summary = "切换会话主策略")
    @PostMapping("/{sessionId}/policy")
    public ResponseEntity<CollabSessionDtos.ActionResponse> switchPolicy(
            @PathVariable String sessionId,
            @RequestBody CollabSessionDtos.PolicySwitchRequest request) {
        return ResponseEntity.ok(sessionService.switchMainAgentPolicy(sessionId, request));
    }
}
