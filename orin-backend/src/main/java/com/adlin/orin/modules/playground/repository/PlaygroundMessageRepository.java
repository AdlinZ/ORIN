package com.adlin.orin.modules.playground.repository;

import com.adlin.orin.modules.playground.entity.PlaygroundMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlaygroundMessageRepository extends JpaRepository<PlaygroundMessageEntity, String> {
    List<PlaygroundMessageEntity> findByConversationIdOrderByCreatedAtAsc(String conversationId);
    void deleteByConversationId(String conversationId);
}
