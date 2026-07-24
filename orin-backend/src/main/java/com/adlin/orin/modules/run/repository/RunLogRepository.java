package com.adlin.orin.modules.run.repository;

import com.adlin.orin.modules.run.entity.RunLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Run 日志数据访问层（F04）。
 */
@Repository
public interface RunLogRepository extends JpaRepository<RunLog, Long> {

    /** 按 runId + 序号升序排列。 */
    List<RunLog> findByRunIdOrderBySequenceAsc(String runId);

    /** 增量拉取：只返回序号大于 afterSeq 的日志行。 */
    List<RunLog> findByRunIdAndSequenceGreaterThanOrderBySequenceAsc(String runId, Integer afterSeq);

    /** 统计某 Run 的日志行数。 */
    long countByRunId(String runId);
}
