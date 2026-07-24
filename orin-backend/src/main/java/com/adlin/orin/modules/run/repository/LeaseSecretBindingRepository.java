package com.adlin.orin.modules.run.repository;

import com.adlin.orin.modules.run.entity.LeaseSecretBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * LeaseSecretBinding 数据访问层（R2 F03 / ADR-002 D-2.8.2）。
 */
@Repository
public interface LeaseSecretBindingRepository extends JpaRepository<LeaseSecretBinding, LeaseSecretBinding.PK> {

    /** 按 assignment_id 查所有 binding（secret-bind 幂等 / renew 检查用）。 */
    List<LeaseSecretBinding> findByAssignmentId(String assignmentId);

    /** 按 secret_id 查所有活跃 binding（revocation 批量 INVALIDATED 用）。 */
    List<LeaseSecretBinding> findBySecretIdAndStatus(String secretId,
                                                      com.adlin.orin.modules.run.entity.BindingStatus status);
}
