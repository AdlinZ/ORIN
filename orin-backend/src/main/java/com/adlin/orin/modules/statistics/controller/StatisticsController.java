package com.adlin.orin.modules.statistics.controller;

import com.adlin.orin.modules.statistics.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

/**
 * 统计分析控制器
 * 提供多维度统计分析功能
 */
@RestController
@RequestMapping("/api/v1/statistics")
@RequiredArgsConstructor
@Tag(name = "Statistics", description = "统计分析")
public class StatisticsController {

    private final StatisticsService statisticsService;

    @Operation(summary = "获取综合统计概览")
    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview() {
        return ResponseEntity.ok(statisticsService.getOverviewStats());
    }

    @Operation(summary = "获取用户活跃度统计")
    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getUserStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(statisticsService.getUserActivityStats(startDate, endDate));
    }

    @Operation(summary = "获取智能体调用统计")
    @GetMapping("/agents")
    public ResponseEntity<Map<String, Object>> getAgentStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(statisticsService.getAgentCallStats(startDate, endDate));
    }

    @Operation(summary = "获取知识库使用统计")
    @GetMapping("/knowledge")
    public ResponseEntity<Map<String, Object>> getKnowledgeStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(statisticsService.getKnowledgeStats(startDate, endDate));
    }

    @Operation(summary = "获取 Token 消耗统计")
    @GetMapping("/tokens")
    public ResponseEntity<Map<String, Object>> getTokenStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(statisticsService.getTokenStats(startDate, endDate));
    }

    @Operation(summary = "获取任务执行统计")
    @GetMapping("/tasks")
    public ResponseEntity<Map<String, Object>> getTaskStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(statisticsService.getTaskStats(startDate, endDate));
    }

    @Operation(summary = "导出统计报表")
    @GetMapping("/export")
    public void exportStatistics(
            @RequestParam String type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletResponse response) throws IOException {
        statisticsService.exportStatistics(type, startDate, endDate, response);
    }
}
