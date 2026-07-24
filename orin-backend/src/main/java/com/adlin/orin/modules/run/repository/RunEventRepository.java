package com.adlin.orin.modules.run.repository;

import com.adlin.orin.modules.run.entity.RunEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * RunEvent 数据访问层（R2 F03）——幂等由 DB UNIQUE 约束保证。
 */
@Repository
public interface RunEventRepository extends JpaRepository<RunEvent, Long> {
}
