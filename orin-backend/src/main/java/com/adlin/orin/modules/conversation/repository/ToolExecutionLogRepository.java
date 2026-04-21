package com.adlin.orin.modules.conversation.repository;

import com.adlin.orin.modules.conversation.entity.ToolExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ToolExecutionLogRepository extends JpaRepository<ToolExecutionLog, Long> {
}
