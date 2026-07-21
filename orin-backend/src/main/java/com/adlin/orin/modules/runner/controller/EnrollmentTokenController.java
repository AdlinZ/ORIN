package com.adlin.orin.modules.runner.controller;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.runner.dto.EnrollmentTokenRequest;
import com.adlin.orin.modules.runner.dto.EnrollmentTokenResponse;
import com.adlin.orin.modules.runner.dto.EnrollmentTokenSummary;
import com.adlin.orin.modules.runner.repository.RunnerEnrollmentTokenRepository;
import com.adlin.orin.modules.runner.service.RunnerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Enrollment Token 业务通道 — Operator/Administrator 在 UI 上创建一次接入令牌。
 *
 * <p>明文 token 仅在创建响应里出现一次；列表 / 删除 / 详情不返回明文，避免敏感信息泄漏。
 * 端点路径 {@code /api/v1/runner-enrollment-tokens/**} 走 JWT 业务通道。
 */
@RestController
@RequestMapping("/api/v1/runner-enrollment-tokens")
@RequiredArgsConstructor
@Tag(name = "Runner F01", description = "Runner 接入业务通道（Enrollment Token）")
public class EnrollmentTokenController {

    private final RunnerService runnerService;
    private final RunnerEnrollmentTokenRepository enrollmentTokenRepository;

    @Value("${orin.runner.enrollment.enrollment-endpoint:/api/system/runners/enroll}")
    private String enrollmentEndpoint;

    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_USER','ROLE_ADMIN','USER','ADMIN')")
    @Operation(summary = "创建 Runner 接入令牌")
    public ResponseEntity<EnrollmentTokenResponse> create(
            @Valid @RequestBody EnrollmentTokenRequest request) {
        String operator = currentPrincipal();
        RunnerService.IssuedEnrollmentToken issued = runnerService.createEnrollmentToken(
                operator, request.name(), request.ttlMinutes());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(EnrollmentTokenResponse.from(issued.token(), issued.plaintext(),
                        enrollmentEndpoint));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_USER','ROLE_ADMIN','USER','ADMIN')")
    @Operation(summary = "列出当前用户创建的接入令牌")
    public List<EnrollmentTokenSummary> list() {
        String operator = currentPrincipal();
        return enrollmentTokenRepository.findByCreatedByOrderByCreatedAtDesc(operator)
                .stream()
                .map(EnrollmentTokenSummary::from)
                .toList();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_USER','ROLE_ADMIN','USER','ADMIN')")
    @Operation(summary = "撤销未使用的接入令牌（已使用或已过期则返回 409）")
    public ResponseEntity<Void> revoke(@PathVariable String id) {
        String operator = currentPrincipal();
        runnerService.revokeEnrollmentToken(id, operator);
        return ResponseEntity.noContent().build();
    }

    private String currentPrincipal() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未认证");
        }
        return String.valueOf(principal);
    }
}
