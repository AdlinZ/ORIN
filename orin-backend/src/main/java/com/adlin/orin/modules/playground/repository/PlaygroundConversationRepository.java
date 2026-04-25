package com.adlin.orin.modules.playground.repository;

import com.adlin.orin.modules.playground.entity.PlaygroundConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlaygroundConversationRepository extends JpaRepository<PlaygroundConversationEntity, String> {
    List<PlaygroundConversationEntity> findByWorkflowIdOrderByUpdatedAtDesc(String workflowId);
    List<PlaygroundConversationEntity> findAllByOrderByUpdatedAtDesc();
}
