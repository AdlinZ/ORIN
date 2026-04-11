package com.adlin.orin.modules.knowledge.repository;

import com.adlin.orin.modules.knowledge.entity.DifyApp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DifyAppRepository extends JpaRepository<DifyApp, String> {
    List<DifyApp> findByAgentId(String agentId);
}
