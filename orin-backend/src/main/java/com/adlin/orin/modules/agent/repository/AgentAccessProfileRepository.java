package com.adlin.orin.modules.agent.repository;

import com.adlin.orin.modules.agent.entity.AgentAccessProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentAccessProfileRepository extends JpaRepository<AgentAccessProfile, String> {
}
