package com.adlin.orin.modules.conversation.repository;

import com.adlin.orin.modules.conversation.entity.ConversationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ConversationLogRepository extends JpaRepository<ConversationLog, String> {

    Page<ConversationLog> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    List<ConversationLog> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    @Query(value = "SELECT cl.id, cl.conversation_id, cl.agent_id, cl.user_id, " +
            "cl.model, cl.query, cl.response, " +
            "cl.prompt_tokens, cl.completion_tokens, cl.total_tokens, " +
            "cl.total_tokens, " +
            "cl.response_time, cl.success, cl.error_message, cl.created_at " +
            "FROM conversation_logs cl " +
            "WHERE cl.created_at = (" +
            "  SELECT MAX(created_at) FROM conversation_logs WHERE conversation_id = cl.conversation_id" +
            ") " +
            "ORDER BY cl.created_at DESC",
            countQuery = "SELECT COUNT(DISTINCT conversation_id) FROM conversation_logs",
            nativeQuery = true)
    Page<Object[]> findGroupedByConversationRaw(Pageable pageable);

    @Query("SELECT c.conversationId, COALESCE(SUM(c.totalTokens), 0) FROM ConversationLog c WHERE c.conversationId IN :conversationIds GROUP BY c.conversationId")
    List<Object[]> findCumulativeTokensByConversationIds(List<String> conversationIds);
}
