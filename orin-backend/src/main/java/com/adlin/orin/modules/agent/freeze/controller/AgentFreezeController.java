package com.adlin.orin.modules.agent.freeze.controller;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.common.snapshot.Sha256Digest;
import com.adlin.orin.modules.agent.freeze.dto.AgentDraftResponse;
import com.adlin.orin.modules.agent.freeze.dto.AgentDraftUpsertRequest;
import com.adlin.orin.modules.agent.freeze.dto.AgentSecretSummary;
import com.adlin.orin.modules.agent.freeze.dto.AgentVersionDetailResponse;
import com.adlin.orin.modules.agent.freeze.dto.AgentVersionListItem;
import com.adlin.orin.modules.agent.freeze.dto.DeprecateVersionRequest;
import com.adlin.orin.modules.agent.freeze.dto.FreezeAgentResponse;
import com.adlin.orin.modules.agent.freeze.dto.SwitchActiveVersionRequest;
import com.adlin.orin.modules.agent.freeze.service.AgentDraftService;
import com.adlin.orin.modules.agent.freeze.service.AgentFreezeService;
import com.adlin.orin.modules.agent.freeze.service.AgentVersionLifecycleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * F02 控制面 REST 端点。
 *
 * <p>路由规划（ADR-002 v4.1 + F02 R3）：
 * <ul>
 *   <li>{@code POST /api/v1/agents} — 创建 Agent（后端生成 id，禁止前端传 id）</li>
 *   <li>{@code GET /api/v1/agents/{agentId}/draft} — 读取草稿（含 active version 指针）</li>
 *   <li>{@code PUT /api/v1/agents/{agentId}/draft} — Upsert 草稿（含 pendingSecretRefs）</li>
 *   <li>{@code POST /api/v1/agents/{agentId}/versions} — 冻结（要求 {@code Idempotency-Key}）</li>
 *   <li>{@code GET /api/v1/agents/{agentId}/versions} — 版本列表</li>
 *   <li>{@code GET /api/v1/agents/{agentId}/versions/{vid}} — 版本详情</li>
 *   <li>{@code PUT /api/v1/agents/{agentId}/active-version} — 切 active 指针</li>
 *   <li>{@code POST /api/v1/agents/{agentId}/versions/{vid}/deprecate} — 标 deprecated</li>
 *   <li>{@code GET /api/v1/agents/_active-gateway-secrets} — 编辑下拉列表</li>
 * </ul>
 *
 * <p>{@code AgentManageController} 上的旧 {@code versions/**} 已迁出；冻结/版本唯一入口由本 Controller 提供。
 *
 * <p>鉴权：JWT 用户角色 {@code ROLE_ADMIN} / {@code ROLE_OPERATOR} / {@code ROLE_USER}(读)。
 * 无 JWT 时 {@link #currentActor()} 抛 {@code AUTH_INVALID_CREDENTIALS} 401，<b>不允许</b>走
 * fallback {@code anonymous}。F02 R3 不再保留 F01-era 的 permitAll 兜底。
 */
@RestController
@RequestMapping("/api/v1/agents")
@Tag(name = "F02: Agent Freeze", description = "创建并冻结 Agent（ADR-002 v4.1）")
public class AgentFreezeController {

    private static final Logger log = LoggerFactory.getLogger(AgentFreezeController.class);
    private static final int IDEMPOTENCY_KEY_MAX_LEN = 200;

    private final AgentDraftService draftService;
    private final AgentFreezeService freezeService;
    private final AgentVersionLifecycleService lifecycleService;
    private final ObjectMapper objectMapper;

    public AgentFreezeController(AgentDraftService draftService,
                                 AgentFreezeService freezeService,
                                 AgentVersionLifecycleService lifecycleService,
                                 ObjectMapper objectMapper) {
        this.draftService = draftService;
        this.freezeService = freezeService;
        this.lifecycleService = lifecycleService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @Operation(summary = "创建 Agent（后端生成 agentId）")
    public AgentDraftResponse createAgent(@RequestBody(required = false) CreateAgentRequest req) {
        String actor = currentActor();
        String name = req == null ? null : req.name();
        String description = req == null ? null : req.description();
        return draftService.createAgent(name, description, actor);
    }

    @GetMapping("/{agentId}/draft")
    @Operation(summary = "查询 Agent 草稿（含 active version 指针）")
    public AgentDraftResponse getDraft(@PathVariable String agentId) {
        return draftService.getDraft(agentId);
    }

    @PutMapping("/{agentId}/draft")
    @Operation(summary = "Upsert Agent 草稿（首次 upsert 即 INSERT；可携带 pendingSecretRefs）")
    public AgentDraftResponse upsertDraft(@PathVariable String agentId,
                                          @Valid @RequestBody AgentDraftUpsertRequest request) {
        String pendingRefsJson;
        try {
            pendingRefsJson = (request.getPendingSecretRefs() == null)
                    ? null
                    : objectMapper.writeValueAsString(request.getPendingSecretRefs());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SNAPSHOT_CANONICALIZE_FAILED,
                    "pendingSecretRefs 序列化失败", e);
        }
        return draftService.upsertDraft(agentId, request, pendingRefsJson, currentActor());
    }

    @PostMapping("/{agentId}/versions")
    @Operation(summary = "冻结 Agent 生成不可变 AgentVersion（要求 Idempotency-Key；secret refs 来自草稿）")
    public FreezeAgentResponse freeze(@PathVariable String agentId,
                                      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.MISSING_IDEMPOTENCY_KEY,
                    "freeze 必须携带 Idempotency-Key HTTP header");
        }
        if (idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LEN) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "Idempotency-Key 长度超过 " + IDEMPOTENCY_KEY_MAX_LEN);
        }
        String idempotencyKeyHash = Sha256Digest.hex(idempotencyKey.getBytes(StandardCharsets.UTF_8));
        String actor = currentActor();
        log.info("F02 freeze request agent={} actor={} idempotencyKeyHash={}", agentId, actor, idempotencyKeyHash);
        return freezeService.freeze(agentId, idempotencyKeyHash, actor);
    }

    @GetMapping("/{agentId}/versions")
    @Operation(summary = "查询 Agent 全部版本列表")
    public List<AgentVersionListItem> listVersions(@PathVariable String agentId) {
        return lifecycleService.listVersions(agentId);
    }

    @GetMapping("/{agentId}/versions/{versionId}")
    @Operation(summary = "查询单个 AgentVersion 详情（FROZEN 完全只读）")
    public AgentVersionDetailResponse getVersion(@PathVariable String agentId,
                                                @PathVariable String versionId) {
        return lifecycleService.getVersion(agentId, versionId);
    }

    @PutMapping("/{agentId}/active-version")
    @Operation(summary = "切换 active version pointer（仅切指针，不触发 deprecate）")
    public AgentVersionDetailResponse switchActiveVersion(@PathVariable String agentId,
                                                          @Valid @RequestBody SwitchActiveVersionRequest request) {
        return lifecycleService.switchActiveVersion(agentId, request.getVersionId(), currentActor());
    }

    @PostMapping("/{agentId}/versions/{versionId}/deprecate")
    @Operation(summary = "Deprecate FROZEN 版本（受控可改字段：status / deprecated_*）")
    public AgentVersionDetailResponse deprecateVersion(@PathVariable String agentId,
                                                       @PathVariable String versionId,
                                                       @Valid @RequestBody DeprecateVersionRequest request) {
        return lifecycleService.deprecateVersion(agentId, versionId, request.getReason(), currentActor());
    }

    @GetMapping("/_active-gateway-secrets")
    @Operation(summary = "查询 ACTIVE 状态的 GatewaySecret 列表（用于前端 SecretReference 编辑下拉）")
    public List<AgentSecretSummary> listActiveGatewaySecrets() {
        return lifecycleService.listActiveGatewaySecrets();
    }

    /**
     * 解析当前请求用户；<b>无 JWT 时抛 401</b>，不允许 fallback 到 {@code anonymous}（F02 R3 要求
     * 写操作必须带角色）。
     */
    private String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()
                || "anonymousUser".equals(auth.getName())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS,
                    "请求必须携带有效 JWT；F02 R3 不再接受 anonymous 上下文");
        }
        return auth.getName();
    }

    /**
     * 创建 Agent 的请求体；轻量字段，全空时按后端默认生成。
     */
    public record CreateAgentRequest(String name, String description) {
    }
}
