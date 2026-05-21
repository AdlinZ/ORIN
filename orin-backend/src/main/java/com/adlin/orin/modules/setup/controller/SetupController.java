package com.adlin.orin.modules.setup.controller;

import com.adlin.orin.modules.audit.service.AuditHelper;
import com.adlin.orin.modules.setup.dto.SetupDtos.InitializeSetupRequest;
import com.adlin.orin.modules.setup.dto.SetupDtos.InitializeSetupResponse;
import com.adlin.orin.modules.setup.dto.SetupDtos.ProviderSetupRequest;
import com.adlin.orin.modules.setup.dto.SetupDtos.SetupStatusResponse;
import com.adlin.orin.modules.setup.service.SetupInitializeService;
import com.adlin.orin.modules.setup.service.SetupStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/setup")
@RequiredArgsConstructor
@Tag(name = "First-run Setup", description = "首次部署初始化向导")
public class SetupController {

    private final SetupStatusService setupStatusService;
    private final SetupInitializeService setupInitializeService;
    private final AuditHelper auditHelper;

    @Operation(summary = "获取首次初始化状态")
    @GetMapping("/status")
    public SetupStatusResponse status() {
        return setupStatusService.getStatus();
    }

    @Operation(summary = "初始化期间测试模型 Provider")
    @PostMapping("/provider/test")
    public ResponseEntity<Map<String, Object>> testProvider(@RequestBody ProviderSetupRequest request) {
        ResponseEntity<Map<String, Object>> guard = guardWritable();
        if (guard != null) {
            return guard;
        }
        try {
            boolean success = setupInitializeService.testProvider(request);
            String provider = request != null ? request.getProvider() : "";
            auditHelper.log("SYSTEM", "SETUP_PROVIDER_TEST", "/api/v1/setup/provider/test",
                    "action=provider-test;provider=" + provider + ";success=" + success,
                    success, success ? null : "PROVIDER_TEST_FAILED");
            return ResponseEntity.ok(Map.of("success", success));
        } catch (IllegalArgumentException e) {
            auditHelper.log("SYSTEM", "SETUP_PROVIDER_TEST", "/api/v1/setup/provider/test",
                    "action=provider-test", false, "INVALID_PROVIDER");
            return badRequest(e.getMessage());
        }
    }

    @Operation(summary = "执行首次初始化")
    @PostMapping("/initialize")
    public ResponseEntity<?> initialize(@RequestBody InitializeSetupRequest request) {
        ResponseEntity<Map<String, Object>> guard = guardWritable();
        if (guard != null) {
            return guard;
        }
        try {
            InitializeSetupResponse response = setupInitializeService.initialize(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (IllegalStateException e) {
            return switch (e.getMessage()) {
                case "SETUP_ALREADY_COMPLETED" -> setupCompleted();
                case "SETUP_DISABLED" -> setupDisabled();
                case "ENCRYPTION_KEY_REQUIRED" -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of(
                                "success", false,
                                "code", "ENCRYPTION_KEY_REQUIRED",
                                "message", "保存 Provider Key 前必须配置 ENCRYPTION_KEY"));
                default -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("success", false, "message", "初始化失败"));
            };
        }
    }

    private ResponseEntity<Map<String, Object>> guardWritable() {
        if (setupStatusService.isSetupCompleted()) {
            return setupCompleted();
        }
        if (!setupStatusService.isSetupWriteEnabled()) {
            return setupDisabled();
        }
        return null;
    }

    private ResponseEntity<Map<String, Object>> setupCompleted() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "success", false,
                        "code", "SETUP_ALREADY_COMPLETED",
                        "message", "系统初始化已完成"));
    }

    private ResponseEntity<Map<String, Object>> setupDisabled() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "success", false,
                        "code", "SETUP_DISABLED",
                        "message", "当前环境未开放首次初始化写入"));
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "success", false,
                        "code", "SETUP_INVALID_REQUEST",
                        "message", message));
    }
}
