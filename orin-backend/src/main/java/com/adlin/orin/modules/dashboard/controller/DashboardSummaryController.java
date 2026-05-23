package com.adlin.orin.modules.dashboard.controller;

import com.adlin.orin.modules.dashboard.service.DashboardSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "角色首页聚合")
public class DashboardSummaryController {

    private final DashboardSummaryService dashboardSummaryService;

    @Operation(summary = "获取角色化首页聚合摘要")
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary(Authentication authentication) {
        return ResponseEntity.ok(dashboardSummaryService.getSummary(authentication));
    }
}
