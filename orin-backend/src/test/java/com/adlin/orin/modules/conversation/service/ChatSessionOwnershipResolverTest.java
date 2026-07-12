package com.adlin.orin.modules.conversation.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.conversation.entity.AgentChatSession;
import com.adlin.orin.modules.conversation.repository.AgentChatSessionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class ChatSessionOwnershipResolverTest {

    private AgentChatSessionRepository sessionRepository;
    private ChatSessionOwnershipResolver resolver;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(AgentChatSessionRepository.class);
        resolver = new ChatSessionOwnershipResolver(sessionRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownerCanManageOwnSession() {
        authenticate("42", "ROLE_USER");
        AgentChatSession owned = session("sid-owned", "agent-1", 42L);
        when(sessionRepository.findBySessionId("sid-owned")).thenReturn(Optional.of(owned));

        assertEquals(42L, resolver.currentUserId());
        AgentChatSession returned = assertDoesNotThrow(() -> resolver.requireOwnedSession("sid-owned"));
        assertSame(owned, returned);
    }

    @Test
    void regularUserCannotReadAnotherUsersSession() {
        authenticate("42", "ROLE_USER");
        when(sessionRepository.findBySessionId("sid-other"))
                .thenReturn(Optional.of(session("sid-other", "agent-1", 99L)));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> resolver.requireOwnedSession("sid-other"));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
    }

    @Test
    void adminCanManageAnySession() {
        authenticate("1", "ROLE_ADMIN");
        when(sessionRepository.findBySessionId("sid-other"))
                .thenReturn(Optional.of(session("sid-other", "agent-1", 99L)));

        assertDoesNotThrow(() -> resolver.requireOwnedSession("sid-other"));
    }

    @Test
    void operatorCanReadLegacyNullOwnerSession() {
        authenticate("5", "ROLE_OPERATOR");
        AgentChatSession legacy = session("sid-legacy", "agent-1", null);
        when(sessionRepository.findBySessionId("sid-legacy")).thenReturn(Optional.of(legacy));

        AgentChatSession returned = assertDoesNotThrow(() -> resolver.requireOwnedSession("sid-legacy"));
        assertSame(legacy, returned);
    }

    @Test
    void missingSessionThrowsNotFound() {
        authenticate("42", "ROLE_USER");
        when(sessionRepository.findBySessionId("sid-missing")).thenReturn(Optional.empty());

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> resolver.requireOwnedSession("sid-missing"));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, error.getErrorCode());
    }

    private void authenticate(String principal, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority(role))));
    }

    private AgentChatSession session(String sessionId, String agentId, Long ownerUserId) {
        AgentChatSession s = new AgentChatSession();
        s.setSessionId(sessionId);
        s.setAgentId(agentId);
        s.setOwnerUserId(ownerUserId);
        return s;
    }
}