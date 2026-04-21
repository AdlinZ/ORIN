package com.adlin.orin.modules.conversation.repository;

import com.adlin.orin.modules.conversation.entity.SessionToolBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SessionToolBindingRepository extends JpaRepository<SessionToolBinding, String> {
    List<SessionToolBinding> findByAgentId(String agentId);
}
