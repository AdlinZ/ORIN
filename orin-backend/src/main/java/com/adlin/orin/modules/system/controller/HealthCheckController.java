package com.adlin.orin.modules.system.controller;

import com.adlin.orin.modules.system.dto.HealthCheckResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;

/**
 * 健康检查控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
@Tag(name = "Health Check", description = "系统健康检查")
public class HealthCheckController {

    private final DataSource dataSource;

    @Operation(summary = "系统健康检查")
    @GetMapping
    public ResponseEntity<HealthCheckResponse> healthCheck() {
        HealthCheckResponse response = new HealthCheckResponse();
        response.setStatus("UP");
        response.setTimestamp(Instant.now().toEpochMilli());
        response.setVersion("1.0.0");

        // 检查数据库
        HealthCheckResponse.ComponentStatus dbStatus = checkDatabase();
        response.setDatabase(dbStatus);

        // 如果数据库不正常，返回 503
        if (!"UP".equals(dbStatus.getStatus())) {
            response.setStatus("DOWN");
            return ResponseEntity.status(503).body(response);
        }

        return ResponseEntity.ok(response);
    }

    private HealthCheckResponse.ComponentStatus checkDatabase() {
        HealthCheckResponse.ComponentStatus status = new HealthCheckResponse.ComponentStatus();
        status.setName("database");
        
        long start = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(5)) {
                status.setStatus("UP");
                status.setMessage("Database connection is healthy");
            } else {
                status.setStatus("DOWN");
                status.setMessage("Database connection is not valid");
            }
        } catch (Exception e) {
            status.setStatus("DOWN");
            status.setMessage("Database connection failed: " + e.getMessage());
            log.error("Database health check failed", e);
        }
        status.setResponseTimeMs(System.currentTimeMillis() - start);
        
        return status;
    }
}
