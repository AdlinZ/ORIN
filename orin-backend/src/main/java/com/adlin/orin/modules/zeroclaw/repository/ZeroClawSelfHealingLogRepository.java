package com.adlin.orin.modules.zeroclaw.repository;

import com.adlin.orin.modules.zeroclaw.entity.ZeroClawSelfHealingLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ZeroClaw 主动维护操作记录 Repository
 */
@Repository
public interface ZeroClawSelfHealingLogRepository extends JpaRepository<ZeroClawSelfHealingLog, String> {

    /**
     * 按操作类型查询
     */
    List<ZeroClawSelfHealingLog> findByActionTypeOrderByCreatedAtDesc(String actionType);

    /**
     * 按状态查询
     */
    List<ZeroClawSelfHealingLog> findByStatusOrderByCreatedAtDesc(String status);

    /**
     * 按目标资源查询
     */
    List<ZeroClawSelfHealingLog> findByTargetResourceOrderByCreatedAtDesc(String targetResource);

    /**
     * 分页查询所有记录
     */
    Page<ZeroClawSelfHealingLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 查询自动执行的操作
     */
    List<ZeroClawSelfHealingLog> findByAutoExecutedTrueOrderByCreatedAtDesc();
}
