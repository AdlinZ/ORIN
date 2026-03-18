package com.adlin.orin.modules.knowledge.repository;

import com.adlin.orin.modules.knowledge.entity.SyncWebhook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SyncWebhookRepository extends JpaRepository<SyncWebhook, Long> {

    List<SyncWebhook> findByAgentIdAndEnabledTrue(String agentId);

    List<SyncWebhook> findByEnabledTrue();
}
