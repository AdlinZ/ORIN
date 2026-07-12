package com.adlin.orin.modules.conversation.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.common.security.BaseOwnershipResolver;
import com.adlin.orin.modules.conversation.entity.AgentChatSession;
import com.adlin.orin.modules.conversation.repository.AgentChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Chat session ownership boundary. Owner is stamped from the JWT principal at create time;
 * sessionId in the path is never trusted for authorization. Legacy NULL-owner sessions
 * remain admin/operator-visible only (fail-closed).
 *
 * <p>Mirrors {@code CollaborationOwnershipService} / {@code KnowledgeOwnershipResolver} —
 * exposes entity-typed helpers only; {@code checkOwnership(Long)} stays {@code protected}.</p>
 */
@Component
@RequiredArgsConstructor
public class ChatSessionOwnershipResolver extends BaseOwnershipResolver {

    private final AgentChatSessionRepository sessionRepository;

    public Long currentUserId() {
        return resolveFromCurrentRequest();
    }

    /**
     * Load the session by public sessionId and assert ownership.
     * Admin/operator (privileged) bypass the ownership check.
     *
     * @throws BusinessException RESOURCE_NOT_FOUND if the sessionId does not exist
     * @throws BusinessException FORBIDDEN if the caller is not the owner and not privileged
     */
    public AgentChatSession requireOwnedSession(String sessionId) {
        AgentChatSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "会话不存在: " + sessionId));
        checkOwnership(session.getOwnerUserId());
        return session;
    }
}