package com.adlin.orin.modules.knowledge.repository;

import com.adlin.orin.modules.knowledge.entity.StructuredDataTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StructuredDataTableRepository extends JpaRepository<StructuredDataTable, Long> {

    /**
     * 根据表名和 Agent ID 查找
     */
    Optional<StructuredDataTable> findByTableNameAndAgentId(String tableName, String agentId);

    /**
     * 根据 Agent ID 查找所有表
     */
    List<StructuredDataTable> findByAgentId(String agentId);

    /**
     * 根据 Agent ID 删除所有表
     */
    void deleteByAgentId(String agentId);
}
