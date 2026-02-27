package com.adlin.orin.modules.zeroclaw.controller;

import com.adlin.orin.modules.zeroclaw.dto.ZeroClawAnalysisRequest;
import com.adlin.orin.modules.zeroclaw.dto.ZeroClawConnectionRequest;
import com.adlin.orin.modules.zeroclaw.dto.ZeroClawSelfHealingRequest;
import com.adlin.orin.modules.zeroclaw.entity.ZeroClawAnalysisReport;
import com.adlin.orin.modules.zeroclaw.entity.ZeroClawConfig;
import com.adlin.orin.modules.zeroclaw.entity.ZeroClawSelfHealingLog;
import com.adlin.orin.modules.zeroclaw.service.ZeroClawService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ZeroClaw 管理控制器
 * 提供轻量化 Agent 集成的 REST API 接口
 */
@RestController
@RequestMapping("/api/zeroclaw")
@RequiredArgsConstructor
public class ZeroClawController {

    private final ZeroClawService zeroClawService;

    // ==================== 配置管理 ====================

    @GetMapping("/configs")
    public ResponseEntity<List<ZeroClawConfig>> getAllConfigs() {
        return ResponseEntity.ok(zeroClawService.getAllConfigs());
    }

    @PostMapping("/configs")
    public ResponseEntity<ZeroClawConfig> createConfig(@RequestBody ZeroClawConfig config) {
        return ResponseEntity.ok(zeroClawService.createConfig(config));
    }

    @PutMapping("/configs/{id}")
    public ResponseEntity<ZeroClawConfig> updateConfig(@PathVariable String id, @RequestBody ZeroClawConfig config) {
        return ResponseEntity.ok(zeroClawService.updateConfig(id, config));
    }

    @DeleteMapping("/configs/{id}")
    public ResponseEntity<Void> deleteConfig(@PathVariable String id) {
        zeroClawService.deleteConfig(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/configs/test-connection")
    public ResponseEntity<Map<String, Object>> testConnection(@RequestBody ZeroClawConnectionRequest request) {
        boolean connected = zeroClawService.testConnection(request.getEndpointUrl(), request.getAccessToken());
        Map<String, Object> result = new HashMap<>();
        result.put("connected", connected);
        result.put("message", connected ? "Connection successful" : "Connection failed");
        return ResponseEntity.ok(result);
    }

    // ==================== 状态监控 ====================

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(zeroClawService.getZeroClawStatus());
    }

    // ==================== 智能分析 ====================

    @PostMapping("/analyze")
    public ResponseEntity<ZeroClawAnalysisReport> performAnalysis(@RequestBody ZeroClawAnalysisRequest request) {
        ZeroClawAnalysisReport report = zeroClawService.performAnalysis(request);
        if (report == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(report);
    }

    @GetMapping("/reports")
    public ResponseEntity<Page<ZeroClawAnalysisReport>> getAnalysisReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(zeroClawService.getAnalysisReports(PageRequest.of(page, size)));
    }

    @GetMapping("/reports/agent/{agentId}")
    public ResponseEntity<List<ZeroClawAnalysisReport>> getAnalysisReportsByAgent(@PathVariable String agentId) {
        return ResponseEntity.ok(zeroClawService.getAnalysisReportsByAgent(agentId));
    }

    @PostMapping("/reports/daily")
    public ResponseEntity<ZeroClawAnalysisReport> generateDailyReport() {
        ZeroClawAnalysisReport report = zeroClawService.generateDailyTrendReport();
        if (report == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(report);
    }

    // ==================== 主动维护 ====================

    @PostMapping("/self-healing")
    public ResponseEntity<ZeroClawSelfHealingLog> executeSelfHealing(@RequestBody ZeroClawSelfHealingRequest request) {
        ZeroClawSelfHealingLog log = zeroClawService.executeSelfHealing(request);
        if (log == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(log);
    }

    @GetMapping("/self-healing/logs")
    public ResponseEntity<Page<ZeroClawSelfHealingLog>> getSelfHealingLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(zeroClawService.getSelfHealingLogs(PageRequest.of(page, size)));
    }
}
