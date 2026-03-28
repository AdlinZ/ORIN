package com.adlin.orin.modules.conversation.repository;

import com.adlin.orin.modules.conversation.entity.AgentChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentChatSessionRepository extends JpaRepository<AgentChatSession, Long> {

    Optional<AgentChatSession> findBySessionId(String sessionId);

    List<AgentChatSession> findByAgentIdOrderByUpdatedAtDesc(String agentId);

    void deleteBySessionId(String sessionId);
}