package com.adlin.orin.modules.knowledge.repository;

import com.adlin.orin.modules.knowledge.entity.DifyConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DifyConversationRepository extends JpaRepository<DifyConversation, String> {
    List<DifyConversation> findByAgentId(String agentId);
    List<DifyConversation> findByAppId(String appId);
}
