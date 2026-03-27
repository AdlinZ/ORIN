package com.adlin.orin.modules.knowledge.repository;

import com.adlin.orin.modules.knowledge.entity.SyncRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SyncRecordRepository extends JpaRepository<SyncRecord, Long> {

    /**
     * 获取 Agent 最后一次同步记录
     */
    Optional<SyncRecord> findTopByAgentIdOrderByEndTimeDesc(String agentId);

    /**
     * 获取 Agent 的同步历史
     */
    List<SyncRecord> findTopByAgentIdOrderByEndTimeDesc(String agentId, Pageable pageable);

    /**
     * 获取 Agent 的所有同步记录（按开始时间倒序）
     */
    List<SyncRecord> findByAgentIdOrderByStartTimeDesc(String agentId);
}
