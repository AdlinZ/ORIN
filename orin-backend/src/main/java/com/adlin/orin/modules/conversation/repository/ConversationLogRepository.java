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

    @Query(value = "SELECT * FROM conversation_logs " +
            "WHERE id IN (" +
            "  SELECT latest_id FROM (" +
            "    SELECT MAX(id) as latest_id FROM conversation_logs " +
            "    GROUP BY conversation_id" +
            "  ) sub" +
            ") " +
            "ORDER BY created_at DESC", countQuery = "SELECT COUNT(DISTINCT conversation_id) FROM conversation_logs", nativeQuery = true)
    Page<ConversationLog> findGroupedByConversation(Pageable pageable);
}
