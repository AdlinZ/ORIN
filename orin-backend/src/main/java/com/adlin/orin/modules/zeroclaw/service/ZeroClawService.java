package com.adlin.orin.modules.zeroclaw.service;

import com.adlin.orin.modules.zeroclaw.dto.ZeroClawAnalysisRequest;
import com.adlin.orin.modules.zeroclaw.dto.ZeroClawSelfHealingRequest;
import com.adlin.orin.modules.zeroclaw.entity.ZeroClawAnalysisReport;
import com.adlin.orin.modules.zeroclaw.entity.ZeroClawConfig;
import com.adlin.orin.modules.zeroclaw.entity.ZeroClawSelfHealingLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

/**
 * ZeroClaw 服务接口
 */
public interface ZeroClawService {

    /**
     * 创建配置
     */
    ZeroClawConfig createConfig(ZeroClawConfig config);

    /**
     * 更新配置
     */
    ZeroClawConfig updateConfig(String id, ZeroClawConfig config);

    /**
     * 删除配置
     */
    void deleteConfig(String id);

    /**
     * 获取所有配置
     */
    List<ZeroClawConfig> getAllConfigs();

    /**
     * 获取启用的配置
     */
    ZeroClawConfig getActiveConfig();

    /**
     * 测试连接
     */
    boolean testConnection(String endpointUrl, String accessToken);

    /**
     * 执行智能分析
     */
    ZeroClawAnalysisReport performAnalysis(ZeroClawAnalysisRequest request);

    /**
     * 获取分析报告列表
     */
    Page<ZeroClawAnalysisReport> getAnalysisReports(Pageable pageable);

    /**
     * 获取指定智能体的分析报告
     */
    List<ZeroClawAnalysisReport> getAnalysisReportsByAgent(String agentId);

    /**
     * 执行主动维护操作
     */
    ZeroClawSelfHealingLog executeSelfHealing(ZeroClawSelfHealingRequest request);

    /**
     * 获取维护操作记录
     */
    Page<ZeroClawSelfHealingLog> getSelfHealingLogs(Pageable pageable);

    /**
     * 获取 ZeroClaw 状态
     */
    Map<String, Object> getZeroClawStatus();

    /**
     * 自动生成 24h 趋势分析报告
     */
    ZeroClawAnalysisReport generateDailyTrendReport();
}
