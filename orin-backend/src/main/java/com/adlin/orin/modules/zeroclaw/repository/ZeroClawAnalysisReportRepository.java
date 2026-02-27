package com.adlin.orin.modules.zeroclaw.repository;

import com.adlin.orin.modules.zeroclaw.entity.ZeroClawAnalysisReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ZeroClaw 分析报告 Repository
 */
@Repository
public interface ZeroClawAnalysisReportRepository extends JpaRepository<ZeroClawAnalysisReport, String> {

    /**
     * 按智能体查询报告
     */
    List<ZeroClawAnalysisReport> findByAgentIdOrderByCreatedAtDesc(String agentId);

    /**
     * 按报告类型查询
     */
    List<ZeroClawAnalysisReport> findByReportTypeOrderByCreatedAtDesc(String reportType);

    /**
     * 分页查询所有报告
     */
    Page<ZeroClawAnalysisReport> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 按严重程度查询
     */
    List<ZeroClawAnalysisReport> findBySeverityOrderByCreatedAtDesc(String severity);
}
