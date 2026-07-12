package com.adlin.orin.modules.conversation.controller;

import com.adlin.orin.modules.conversation.dto.ConversationSummary;
import com.adlin.orin.modules.conversation.entity.ConversationLog;
import com.adlin.orin.modules.conversation.service.ConversationLogService;
import com.adlin.orin.modules.conversation.service.ChatSessionOwnershipResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/conversation-logs")
@RequiredArgsConstructor
@Tag(name = "Conversation Logs", description = "智能体对话日志接口")
public class ConversationLogController {

    private final ConversationLogService service;
    private final ChatSessionOwnershipResolver ownershipResolver;

    @Operation(summary = "按会话分组获取日志")
    @GetMapping("/grouped")
    public Page<ConversationSummary> getGroupedLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        if (ownershipResolver.isCurrentUserPrivileged()) {
            return service.getGroupedLogs(PageRequest.of(page, size));
        }
        return service.getGroupedLogsForUser(
                ownershipResolver.currentUserId().toString(),
                PageRequest.of(page, size));
    }

    @Operation(summary = "获取指定会话的历史记录")
    @GetMapping("/{conversationId}/history")
    public List<ConversationLog> getHistory(@PathVariable String conversationId) {
        if (ownershipResolver.isCurrentUserPrivileged()) {
            return service.getConversationHistory(conversationId);
        }
        ownershipResolver.requireOwnedSession(conversationId);
        return service.getConversationHistoryForUser(
                conversationId,
                ownershipResolver.currentUserId().toString());
    }
}
