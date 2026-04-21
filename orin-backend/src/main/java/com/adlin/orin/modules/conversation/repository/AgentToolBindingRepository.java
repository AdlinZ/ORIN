package com.adlin.orin.modules.conversation.repository;

import com.adlin.orin.modules.conversation.entity.AgentToolBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentToolBindingRepository extends JpaRepository<AgentToolBinding, String> {
}
