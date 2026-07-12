package com.adlin.orin.modules.conversation.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adlin.orin.modules.conversation.service.ChatSessionOwnershipResolver;
import com.adlin.orin.modules.conversation.service.ConversationLogService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

class ConversationLogControllerTest {

    @Test
    void regularUserOnlyReadsOwnGroupedLogs() {
        ConversationLogService service = mock(ConversationLogService.class);
        ChatSessionOwnershipResolver ownership = mock(ChatSessionOwnershipResolver.class);
        when(ownership.isCurrentUserPrivileged()).thenReturn(false);
        when(ownership.currentUserId()).thenReturn(42L);
        when(service.getGroupedLogsForUser(org.mockito.ArgumentMatchers.eq("42"),
                org.mockito.ArgumentMatchers.any(Pageable.class))).thenReturn(Page.empty());

        ConversationLogController controller = new ConversationLogController(service, ownership);
        controller.getGroupedLogs(0, 15);

        verify(service).getGroupedLogsForUser(
                org.mockito.ArgumentMatchers.eq("42"),
                org.mockito.ArgumentMatchers.any(Pageable.class));
    }

    @Test
    void regularUserHistoryRequiresOwnedSessionAndUserFilter() {
        ConversationLogService service = mock(ConversationLogService.class);
        ChatSessionOwnershipResolver ownership = mock(ChatSessionOwnershipResolver.class);
        when(ownership.isCurrentUserPrivileged()).thenReturn(false);
        when(ownership.currentUserId()).thenReturn(42L);

        ConversationLogController controller = new ConversationLogController(service, ownership);
        controller.getHistory("session-42");

        verify(ownership).requireOwnedSession("session-42");
        verify(service).getConversationHistoryForUser("session-42", "42");
    }

    @Test
    void adminKeepsGlobalConversationLogView() {
        ConversationLogService service = mock(ConversationLogService.class);
        ChatSessionOwnershipResolver ownership = mock(ChatSessionOwnershipResolver.class);
        when(ownership.isCurrentUserPrivileged()).thenReturn(true);
        when(service.getGroupedLogs(org.mockito.ArgumentMatchers.any(Pageable.class))).thenReturn(Page.empty());

        ConversationLogController controller = new ConversationLogController(service, ownership);
        controller.getGroupedLogs(0, 15);
        controller.getHistory("session-any");

        verify(service).getGroupedLogs(org.mockito.ArgumentMatchers.any(Pageable.class));
        verify(service).getConversationHistory("session-any");
    }
}
