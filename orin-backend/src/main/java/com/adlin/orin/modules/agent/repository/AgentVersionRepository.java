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
     * 获取指定智能体的所有版本 (按版本号降序)
     */
    List<AgentVersion> findByAgentIdOrderByVersionNumberDesc(String agentId);

    /**
     * 获取指定智能体的当前激活版本
     */
    Optional<AgentVersion> findByAgentIdAndIsActiveTrue(String agentId);

    /**
     * 获取指定智能体的最大版本号
     */
    @Query("SELECT MAX(v.versionNumber) FROM AgentVersion v WHERE v.agentId = :agentId")
    Optional<Integer> findMaxVersionNumber(@Param("agentId") String agentId);

    /**
     * 获取指定智能体的特定版本
     */
    Optional<AgentVersion> findByAgentIdAndVersionNumber(String agentId, Integer versionNumber);

    /**
     * 统计指定智能体的版本数
     */
    long countByAgentId(String agentId);

    /**
     * 删除指定智能体的所有版本
     */
    void deleteByAgentId(String agentId);
}
