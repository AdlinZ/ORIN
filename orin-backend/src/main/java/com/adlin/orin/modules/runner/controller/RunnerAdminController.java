package com.adlin.orin.modules.runner.controller;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.runner.dto.RunnerDetail;
import com.adlin.orin.modules.runner.dto.RunnerListItem;
import com.adlin.orin.modules.runner.entity.Runner;
import com.adlin.orin.modules.runner.entity.RunnerCredential;
import com.adlin.orin.modules.runner.entity.RunnerHeartbeatSnapshot;
import com.adlin.orin.modules.runner.repository.RunnerHeartbeatSnapshotRepository;
import com.adlin.orin.modules.runner.service.RunnerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * Runner 业务通道 — Operator/Administrator 在 Workspace Runner 页面使用的接口。
 *
 * <p>列表 / 详情 / 状态机 ops（drain / restore / revoke）走 JWT 业务通道，路径前缀
 * {@code /api/v1/runners/**}。机器通道（enroll / heartbeat）由 {@code /api/system/runners/**} 单独
 * 端点承载，由 {@code RunnerCredentialAuthFilter} / {@code EnrollmentTokenAuthFilter} 鉴权。
 */
@RestController
@RequestMapping("/api/v1/runners")
@RequiredArgsConstructor
@Tag(name = "Runner F01", description = "Runner 接入业务通道")
public class RunnerAdminController {

    private final RunnerService runnerService;
    private final RunnerHeartbeatSnapshotRepository heartbeatSnapshotRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_USER','ROLE_ADMIN','USER','ADMIN')")
    @Operation(summary = "列出 Runner（按更新时间倒序）")
    public Page<RunnerListItem> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        return runnerService.listRunners(pageable).map(RunnerListItem::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_USER','ROLE_ADMIN','USER','ADMIN')")
    @Operation(summary = "Runner 详情 + 最新资源快照 + 凭据摘要")
    public RunnerDetail detail(@PathVariable String id) {
        Runner runner = runnerService.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Runner 不存在: " + id));
        List<RunnerHeartbeatSnapshot> recent = heartbeatSnapshotRepository
                .findByRunnerIdOrderByReportedAtDesc(runner.getId(), PageRequest.of(0, 20));
        RunnerHeartbeatSnapshot latest = recent.isEmpty() ? null : recent.get(0);
        Optional<RunnerCredential> credential = runnerService
                .findActiveCredentialByRunnerId(runner.getId());
        return RunnerDetail.from(runner, latest, recent,
                credential.orElse(null));
    }

    @PostMapping("/{id}/drain")
    @PreAuthorize("hasAnyRole('ROLE_USER','ROLE_ADMIN','USER','ADMIN')")
    @Operation(summary = "Drain Runner（停止领取新 Run，等待当前 Run 结束）")
    public RunnerListItem drain(@PathVariable String id) {
        return RunnerListItem.from(runnerService.drain(id, currentPrincipal()));
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('ROLE_USER','ROLE_ADMIN','USER','ADMIN')")
    @Operation(summary = "取消 Drain；DRAINING 恢复 ONLINE，OFFLINE 保持离线等待真实心跳")
    public RunnerListItem restore(@PathVariable String id) {
        return RunnerListItem.from(runnerService.restore(id, currentPrincipal()));
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ADMIN')")
    @Operation(summary = "撤销 Runner Credential（任何状态 → REVOKED 终态）")
    public ResponseEntity<RunnerListItem> revoke(@PathVariable String id) {
        return ResponseEntity.ok(RunnerListItem.from(
                runnerService.revoke(id, currentPrincipal())));
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
