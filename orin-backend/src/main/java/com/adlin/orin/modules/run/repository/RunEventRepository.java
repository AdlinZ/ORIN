package com.adlin.orin.modules.run.repository;

import com.adlin.orin.modules.run.entity.RunEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * RunEvent 数据访问层（R2 F03）——幂等由 DB UNIQUE 约束保证。
 */
@Repository
public interface RunEventRepository extends JpaRepository<RunEvent, Long> {

    Optional<RunEvent> findByRunIdAndLeaseIdAndRunAttemptAndEventSeq(
            String runId, String leaseId, Integer runAttempt, Integer eventSeq);

    /** F04：按 runId + eventSeq 升序返回所有事件。 */
    List<RunEvent> findByRunIdOrderByEventSeqAsc(String runId);

    /** F04：增量拉取事件（eventSeq > afterSeq）。 */
    List<RunEvent> findByRunIdAndEventSeqGreaterThanOrderByEventSeqAsc(String runId, Integer afterSeq);
}
