package com.adlin.orin.modules.agent.repository;

import com.adlin.orin.modules.agent.entity.AgentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentVersionRepository extends JpaRepository<AgentVersion, String> {

    /**
     * 获取指定智能体的所有版本（按版本号降序）。
     */
    List<AgentVersion> findByAgentIdOrderByVersionNumberDesc(String agentId);

    /**
     * 获取指定智能体的当前激活版本。
     *
     * @deprecated since F02 R3 — read {@code AgentMetadata.active_version_id} instead.
     * Kept for legacy callers; new code should use {@code agentMetadataRepository.findById(...)} +
     * lookup the version via this repository's {@code findById}.
     */
    @Deprecated
    Optional<AgentVersion> findByAgentIdAndIsActiveTrue(String agentId);

    /**
     * 获取指定智能体的最大版本号。
     */
    @Query("SELECT MAX(v.versionNumber) FROM AgentVersion v WHERE v.agentId = :agentId")
    Optional<Integer> findMaxVersionNumber(@Param("agentId") String agentId);

    /**
     * 获取指定智能体的特定版本号。
     */
    Optional<AgentVersion> findByAgentIdAndVersionNumber(String agentId, Integer versionNumber);

    /**
     * 统计指定智能体的版本数。
     */
    long countByAgentId(String agentId);

    /**
     * 删除指定智能体的所有版本。
     *
     * @deprecated since F02 R3 — AgentVersion 不允许 DELETE；该方法仅由遗留路径调用。
     */
    @Deprecated
    void deleteByAgentId(String agentId);

    // ===== F02 R3 新查询（ADR-002） =====

    /** 按 id 查找（用于 digest lookup）。 */
    Optional<AgentVersion> findById(String id);

    /** 校验某 versionId 是否属于该 agent 并且状态为 FROZEN。 */
    Optional<AgentVersion> findByIdAndAgentIdAndStatus(String id, String agentId, AgentVersion.Status status);

    /** 同上，但接受 DEPRECATED 状态（用于 active switch 校验传入的目标）。 */
    Optional<AgentVersion> findByIdAndAgentId(String id, String agentId);

    /** 列出指定状态的版本（用于 lifecycle 过滤）。 */
    List<AgentVersion> findByAgentIdAndStatusOrderByVersionNumberDesc(String agentId, AgentVersion.Status status);
}
