package com.adlin.orin.modules.collaboration.service;

import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.collaboration.dto.CollabSessionDtos;
import com.adlin.orin.modules.collaboration.dto.CollaborationPackage;
import com.adlin.orin.modules.collaboration.entity.CollabMessageEntity;
import com.adlin.orin.modules.collaboration.entity.CollabSessionEntity;
import com.adlin.orin.modules.collaboration.entity.CollabSubtaskEntity;
import com.adlin.orin.modules.collaboration.entity.CollabTurnEntity;
import com.adlin.orin.modules.collaboration.entity.CollaborationPackageEntity;
import com.adlin.orin.modules.collaboration.repository.CollabMessageRepository;
import com.adlin.orin.modules.collaboration.repository.CollabSessionRepository;
import com.adlin.orin.modules.collaboration.repository.CollabTurnRepository;
import com.adlin.orin.modules.collaboration.repository.CollabEventLogRepository;
import com.adlin.orin.modules.collaboration.repository.CollaborationPackageRepository;
import com.adlin.orin.modules.collaboration.service.runtime.AgentRuntimeState;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollaborationSessionService {
    private static final Set<String> TEXT_ONLY_ROLES_IN_IMAGE_TASK = Set.of(
            "PLANNER", "REVIEWER", "CRITIC", "COORDINATOR"
    );
    private static final List<String> IMAGE_EXECUTION_KEYWORDS = List.of(
            "执行图像生成", "生成图像", "出图", "绘制图像", "绘制一张", "调用文生图", "直接生成图片",
            "generate image", "text to image", "text-to-image", "render image", "create image"
    );

    private final CollabSessionRepository sessionRepository;
    private final CollabTurnRepository turnRepository;
    private final CollabMessageRepository messageRepository;
    private final CollabEventLogRepository eventLogRepository;
    private final CollaborationOrchestrator orchestrator;
    private final CollaborationExecutor executor;
    private final CollaborationArbiterService arbiterService;
    private final CollaborationRedisService redisService;
    private final CollaborationMemoryService memoryService;
    private final CollaborationSessionStreamService streamService;
    private final CollaborationPackageRepository packageRepository;
    private final AmqpAdmin amqpAdmin;
    private final ObjectMapper objectMapper;

    @Value("${orin.collaboration.dlq.name:collaboration-task-dlq}")
    private String collaborationDlqName;
    @Value("${orin.collab.metrics.alert.success-rate.warn:0.85}")
    private double successRateWarn;
    @Value("${orin.collab.metrics.alert.success-rate.critical:0.70}")
    private double successRateCritical;
    @Value("${orin.collab.metrics.alert.p95-ms.warn:20000}")
    private double p95WarnMs;
    @Value("${orin.collab.metrics.alert.p95-ms.critical:60000}")
    private double p95CriticalMs;
    @Value("${orin.collab.metrics.alert.dlq.warn:1}")
    private long dlqWarn;
    @Value("${orin.collab.metrics.alert.dlq.critical:20}")
    private long dlqCritical;
    @Value("${orin.collab.metrics.alert.bidding-success.warn:0.70}")
    private double biddingSuccessWarn;
    @Value("${orin.collab.metrics.alert.bidding-success.critical:0.50}")
    private double biddingSuccessCritical;
    @Value("${orin.collab.metrics.alert.avg-critique.warn:2.5}")
    private double avgCritiqueWarn;
    @Value("${orin.collab.metrics.alert.avg-critique.critical:3.5}")
    private double avgCritiqueCritical;

    @Transactional
    public CollabSessionDtos.SessionView createSession(CollabSessionDtos.SessionCreateRequest request, String userId) {
        CollabSessionEntity entity = CollabSessionEntity.builder()
                .sessionId(UUID.randomUUID().toString().replace("-", ""))
                .title(request.getTitle() != null && !request.getTitle().isBlank() ? request.getTitle() : "协作会话")
                .status("ACTIVE")
                .mainAgentPolicy(defaultString(request.getMainAgentPolicy(), "STATIC_THEN_BID"))
                .qualityThreshold(request.getQualityThreshold() != null ? request.getQualityThreshold() : 0.82)
                .maxCritiqueRounds(request.getMaxCritiqueRounds() != null ? request.getMaxCritiqueRounds() : 3)
                .draftParallelism(request.getDraftParallelism() != null ? request.getDraftParallelism() : 4)
                .mainAgentStaticDefault(request.getMainAgentStaticDefault())
                .bidWhitelist(toJson(request.getBidWhitelist() != null ? request.getBidWhitelist() : List.of()))
                .createdBy(userId)
                .build();
        return toSessionView(sessionRepository.save(entity));
    }

    public List<CollabSessionDtos.SessionView> listSessions(String userId) {
        List<CollabSessionEntity> sessions = userId != null && !userId.isBlank()
                ? sessionRepository.findByCreatedByOrderByUpdatedAtDesc(userId)
                : sessionRepository.findAllByOrderByUpdatedAtDesc();
        return sessions.stream().map(this::toSessionView).toList();
    }

    @Transactional
    public CollabSessionDtos.TurnStartResponse sendMessage(String sessionId,
                                                           CollabSessionDtos.SessionMessageRequest request,
                                                           String userId,
                                                           String traceId) {
        CollabSessionEntity session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.save(session);

        String turnId = UUID.randomUUID().toString().replace("-", "");
        saveMessage(sessionId, turnId, "user", "USER_INPUT", request.getContent(), Map.of());

        CollabTurnEntity turn = CollabTurnEntity.builder()
                .turnId(turnId)
                .sessionId(sessionId)
                .traceId(traceId)
                .userMessage(request.getContent())
                .status("RUNNING")
                .build();
        turnRepository.save(turn);

        runTurnAsync(session, turn, request, userId, traceId);

        return CollabSessionDtos.TurnStartResponse.builder()
                .sessionId(sessionId)
                .turnId(turnId)
                .status("RUNNING")
                .build();
    }

    public List<CollabSessionDtos.MessageView> listMessages(String sessionId, String turnId, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        var data = (turnId != null && !turnId.isBlank())
                ? messageRepository.findBySessionIdAndTurnIdOrderByCreatedAtAsc(sessionId, turnId, pageable)
                : messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId, pageable);

        return data.getContent().stream().map(this::toMessageView).toList();
    }

    public CollabSessionDtos.SessionStateView getState(String sessionId, String turnId) {
        CollabSessionEntity session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        Optional<CollabTurnEntity> turnOpt = (turnId != null && !turnId.isBlank())
                ? turnRepository.findByTurnId(turnId)
                : turnRepository.findFirstBySessionIdOrderByStartedAtDesc(sessionId);

        Map<String, Object> runtime = Map.of();
        Map<String, Object> selection = Map.of();
        List<Map<String, Object>> timeline = List.of();
        List<Map<String, Object>> branches = List.of();
        Map<String, Object> arbiter = Map.of();
        List<Map<String, Object>> evidenceRefs = List.of();
        Map<String, Boolean> uiActions = defaultUiActions(false, false, false);
        List<String> operatorHints = new ArrayList<>();
        String packageId = null;
        String latestTurnId = null;
        String latestTurnStatus = null;

        if (turnOpt.isPresent()) {
            CollabTurnEntity turn = turnOpt.get();
            packageId = turn.getPackageId();
            latestTurnId = turn.getTurnId();
            latestTurnStatus = turn.getStatus();

            if (packageId != null && !packageId.isBlank()) {
                try {
                    runtime = orchestrator.getRuntimeStatus(packageId);
                    Object selectionObj = runtime.get("selection");
                    if (selectionObj instanceof Map<?, ?> map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> cast = (Map<String, Object>) map;
                        selection = cast;
                    }
                } catch (Exception ignored) {
                }
                branches = buildBranches(packageId);
                if (selection.isEmpty()) {
                    selection = inferLatestSelectionFromBranches(branches);
                }
                evidenceRefs = collectEvidenceRefs(branches);
                arbiter = buildArbiterSnapshot(sessionId, turn.getTurnId());
                uiActions = buildUiActions(packageId, latestTurnStatus, branches);
                operatorHints = buildOperatorHints(packageId, branches, latestTurnStatus);
            }

            if (selection.isEmpty()) {
                selection = readMapJson(turn.getSelectionMeta());
            }

            timeline = buildTimeline(sessionId, turn.getTurnId());
        }

        return CollabSessionDtos.SessionStateView.builder()
                .sessionId(session.getSessionId())
                .status(session.getStatus())
                .latestTurnId(latestTurnId)
                .latestTurnStatus(latestTurnStatus)
                .packageId(packageId)
                .runtime(runtime)
                .selection(selection)
                .timeline(timeline)
                .branches(branches)
                .arbiter(arbiter)
                .evidenceRefs(evidenceRefs)
                .uiActions(uiActions)
                .operatorHints(operatorHints)
                .build();
    }

    public CollabSessionDtos.SessionMetricsView getSessionMetrics(int hours) {
        int safeHours = hours <= 0 ? 24 : Math.min(hours, 24 * 30);
        LocalDateTime start = LocalDateTime.now().minusHours(safeHours);

        List<CollabTurnEntity> turns = turnRepository.findByStartedAtAfter(start);
        long totalTurns = turns.size();
        long successTurns = turns.stream().filter(t -> "COMPLETED".equalsIgnoreCase(t.getStatus())).count();
        double successRate = totalTurns > 0 ? (double) successTurns / totalTurns : 0.0;

        List<Long> latencies = turns.stream()
                .filter(t -> "COMPLETED".equalsIgnoreCase(t.getStatus()) && t.getCompletedAt() != null && t.getStartedAt() != null)
                .map(t -> java.time.Duration.between(t.getStartedAt(), t.getCompletedAt()).toMillis())
                .filter(ms -> ms >= 0)
                .sorted()
                .collect(Collectors.toList());
        double p95LatencyMs = computeP95(latencies);

        long biddingTriggeredTurns = turns.stream().filter(this::isBiddingTriggered).count();
        long biddingSuccessTurns = turns.stream()
                .filter(this::isBiddingTriggered)
                .filter(t -> "COMPLETED".equalsIgnoreCase(t.getStatus()))
                .count();
        double biddingTriggerRate = totalTurns > 0 ? (double) biddingTriggeredTurns / totalTurns : 0.0;
        double biddingPostSuccessRate = biddingTriggeredTurns > 0
                ? (double) biddingSuccessTurns / biddingTriggeredTurns
                : 0.0;

        long critiqueRounds = messageRepository.countByStageAndCreatedAtAfter("CRITIQUE_ROUND", start);
        double avgCritiqueRounds = totalTurns > 0 ? (double) critiqueRounds / totalTurns : 0.0;
        long dlqBacklog = getDlqBacklog();
        Map<String, String> metricLevels = new HashMap<>();
        List<String> alerts = new ArrayList<>();

        String successLevel = levelForInverse(successRate, successRateWarn, successRateCritical);
        metricLevels.put("successRate", successLevel);
        if (!"GREEN".equals(successLevel)) {
            alerts.add("会话成功率偏低: " + formatPercent(successRate));
        }

        String p95Level = levelForDirect(p95LatencyMs, p95WarnMs, p95CriticalMs);
        metricLevels.put("p95LatencyMs", p95Level);
        if (!"GREEN".equals(p95Level)) {
            alerts.add("P95 时延偏高: " + Math.round(p95LatencyMs) + "ms");
        }

        String dlqLevel = levelForDirect(dlqBacklog, dlqWarn, dlqCritical);
        metricLevels.put("dlqBacklog", dlqLevel);
        if (!"GREEN".equals(dlqLevel)) {
            alerts.add("DLQ 存在积压: " + dlqBacklog);
        }

        String critiqueLevel = levelForDirect(avgCritiqueRounds, avgCritiqueWarn, avgCritiqueCritical);
        metricLevels.put("avgCritiqueRounds", critiqueLevel);
        if (!"GREEN".equals(critiqueLevel)) {
            alerts.add("平均 Critique 轮次偏高: " + String.format("%.2f", avgCritiqueRounds));
        }

        String biddingSuccessLevel = "GREEN";
        if (biddingTriggeredTurns > 0) {
            biddingSuccessLevel = levelForInverse(biddingPostSuccessRate, biddingSuccessWarn, biddingSuccessCritical);
            if (!"GREEN".equals(biddingSuccessLevel)) {
                alerts.add("竞标后成功率偏低: " + formatPercent(biddingPostSuccessRate));
            }
        }
        metricLevels.put("biddingPostSuccessRate", biddingSuccessLevel);

        String overallLevel = computeOverallLevel(metricLevels);

        return CollabSessionDtos.SessionMetricsView.builder()
                .hours(safeHours)
                .totalTurns(totalTurns)
                .successTurns(successTurns)
                .successRate(successRate)
                .p95LatencyMs(p95LatencyMs)
                .dlqBacklog(dlqBacklog)
                .biddingTriggeredTurns(biddingTriggeredTurns)
                .biddingTriggerRate(biddingTriggerRate)
                .biddingPostSuccessRate(biddingPostSuccessRate)
                .avgCritiqueRounds(avgCritiqueRounds)
                .overallLevel(overallLevel)
                .metricLevels(metricLevels)
                .alerts(alerts)
                .build();
    }

    public List<CollabSessionDtos.ModelCapabilityView> listModelCapabilities(CollabSessionDtos.ModelCapabilityRequest request) {
        String taskText = request != null && request.getTaskText() != null ? request.getTaskText() : "";
        String expectedRole = request != null && request.getExpectedRole() != null ? request.getExpectedRole() : "";
        List<Map<String, Object>> capabilities = executor.listModelCapabilities(
                executor.getAvailableAgents(),
                taskText,
                expectedRole
        );
        return capabilities.stream()
                .map(this::toModelCapabilityView)
                .toList();
    }

    @Transactional
    public CollabSessionDtos.ActionResponse pauseTurn(String sessionId, String turnId) {
        CollabTurnEntity turn = turnRepository.findByTurnId(turnId)
                .orElseThrow(() -> new RuntimeException("Turn not found: " + turnId));
        if (!sessionId.equals(turn.getSessionId())) {
            throw new RuntimeException("Turn does not belong to session");
        }
        if (turn.getPackageId() == null || turn.getPackageId().isBlank()) {
            return CollabSessionDtos.ActionResponse.builder()
                    .success(false)
                    .message("回合尚未进入执行状态")
                    .data(Map.of())
                    .build();
        }
        redisService.updateContextField(turn.getPackageId(), "turnPaused", true);
        publish(sessionId, turnId, "ROUND_GUARDRAIL_TRIGGERED", "ROUND_GUARDRAIL_TRIGGERED",
                "回合已暂停，可继续观察或恢复执行", Map.of("action", "pause"));
        return CollabSessionDtos.ActionResponse.builder()
                .success(true)
                .message("回合已暂停")
                .data(Map.of("turnId", turnId))
                .build();
    }

    @Transactional
    public CollabSessionDtos.ActionResponse resumeTurn(String sessionId, String turnId) {
        CollabTurnEntity turn = turnRepository.findByTurnId(turnId)
                .orElseThrow(() -> new RuntimeException("Turn not found: " + turnId));
        if (!sessionId.equals(turn.getSessionId())) {
            throw new RuntimeException("Turn does not belong to session");
        }
        if (turn.getPackageId() == null || turn.getPackageId().isBlank()) {
            return CollabSessionDtos.ActionResponse.builder()
                    .success(false)
                    .message("回合尚未进入执行状态")
                    .data(Map.of())
                    .build();
        }
        redisService.updateContextField(turn.getPackageId(), "turnPaused", false);
        publish(sessionId, turnId, "ROUND_GUARDRAIL_TRIGGERED", "ROUND_GUARDRAIL_TRIGGERED",
                "回合已恢复执行", Map.of("action", "resume"));
        return CollabSessionDtos.ActionResponse.builder()
                .success(true)
                .message("回合已恢复")
                .data(Map.of("turnId", turnId))
                .build();
    }

    @Transactional
    public CollabSessionDtos.ActionResponse switchMainAgentPolicy(String sessionId, CollabSessionDtos.PolicySwitchRequest request) {
        String policy = defaultString(request != null ? request.getMainAgentPolicy() : null, "STATIC_THEN_BID");
        if (!List.of("STATIC_THEN_BID", "BID_FIRST").contains(policy)) {
            return CollabSessionDtos.ActionResponse.builder()
                    .success(false)
                    .message("不支持的策略: " + policy)
                    .data(Map.of("allowed", List.of("STATIC_THEN_BID", "BID_FIRST")))
                    .build();
        }

        CollabSessionEntity session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));
        session.setMainAgentPolicy(policy);
        sessionRepository.save(session);

        turnRepository.findFirstBySessionIdOrderByStartedAtDesc(sessionId)
                .ifPresent(turn -> {
                    if (turn.getPackageId() != null && !turn.getPackageId().isBlank()) {
                        packageRepository.findByPackageId(turn.getPackageId())
                                .ifPresent(pkg -> {
                                    Map<String, Object> strategy = readMapJson(pkg.getStrategy());
                                    strategy.put("mainAgentPolicy", policy);
                                    pkg.setStrategy(toJson(strategy));
                                    packageRepository.save(pkg);
                                });
                        publish(sessionId, turn.getTurnId(), "ROUND_GUARDRAIL_TRIGGERED", "ROUND_GUARDRAIL_TRIGGERED",
                                "主策略已切换为 " + policy, Map.of("action", "switch_policy", "policy", policy));
                    }
                });

        return CollabSessionDtos.ActionResponse.builder()
                .success(true)
                .message("策略已更新")
                .data(Map.of("mainAgentPolicy", policy))
                .build();
    }

    public List<CollabSessionDtos.StreamEvent> replayEvents(String sessionId, String turnId) {
        return messageRepository.findBySessionIdAndTurnIdOrderByCreatedAtAsc(
                        sessionId,
                        turnId,
                        PageRequest.of(0, 200))
                .stream()
                .map(m -> CollabSessionDtos.StreamEvent.builder()
                        .eventType(defaultString(m.getStage(), "MESSAGE"))
                        .sessionId(m.getSessionId())
                        .turnId(m.getTurnId())
                        .stage(m.getStage())
                        .content(m.getContent())
                        .data(readMapJson(m.getMetadata()))
                        .timestamp(m.getCreatedAt() != null ? m.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : System.currentTimeMillis())
                        .build())
                .toList();
    }

    private CollabSessionDtos.ModelCapabilityView toModelCapabilityView(Map<String, Object> row) {
        List<String> supports = List.of();
        Object supportsObj = row.get("supports");
        if (supportsObj instanceof List<?> list) {
            supports = list.stream().map(String::valueOf).toList();
        }
        return CollabSessionDtos.ModelCapabilityView.builder()
                .agentId(String.valueOf(row.getOrDefault("agentId", "")))
                .name(String.valueOf(row.getOrDefault("name", "")))
                .model(String.valueOf(row.getOrDefault("model", "")))
                .provider(String.valueOf(row.getOrDefault("provider", "")))
                .type(String.valueOf(row.getOrDefault("type", "")))
                .supports(supports)
                .healthy(Boolean.TRUE.equals(row.get("healthy")))
                .eligible(Boolean.TRUE.equals(row.get("eligible")))
                .intentMatched(Boolean.TRUE.equals(row.get("intentMatched")))
                .roleMatched(Boolean.TRUE.equals(row.get("roleMatched")))
                .imageCapable(Boolean.TRUE.equals(row.get("imageCapable")))
                .build();
    }

    private void runTurnAsync(CollabSessionEntity session,
                              CollabTurnEntity turn,
                              CollabSessionDtos.SessionMessageRequest request,
                              String userId,
                              String traceId) {
        CompletableFuture.runAsync(() -> {
            String sessionId = session.getSessionId();
            String turnId = turn.getTurnId();
            try {
                publish(sessionId, turnId, "TURN_STARTED", "TURN_STARTED", "协作回合已启动", Map.of());
                publish(sessionId, turnId, "ORCHESTRATE_STARTED", "ORCHESTRATE_STARTED", "进入编排阶段", Map.of());

                Map<String, Object> strategyOverrides = new HashMap<>();
                strategyOverrides.put("mainAgentPolicy", session.getMainAgentPolicy());
                strategyOverrides.put("qualityThreshold", session.getQualityThreshold());
                strategyOverrides.put("maxCritiqueRounds", session.getMaxCritiqueRounds());
                strategyOverrides.put("draftParallelism", session.getDraftParallelism());
                strategyOverrides.put("mainAgentStaticDefault", session.getMainAgentStaticDefault());
                strategyOverrides.put("bidWhitelist", readStringListJson(session.getBidWhitelist()));

                String category = defaultString(request.getCategory(), "GENERATION");
                String priority = defaultString(request.getPriority(), "NORMAL");
                String complexity = defaultString(request.getComplexity(), "MEDIUM");
                String mode = defaultString(request.getCollaborationMode(), "PARALLEL");
                String executionProfile = defaultString(request.getExecutionProfile(), "PROD_CONTROLLED");
                String workloadType = defaultString(request.getWorkloadType(), "RND_COMPLEX");
                String failurePolicy = defaultString(request.getFailurePolicy(), "AUTO_DEGRADE_CONTINUE");

                CollaborationPackage pkg = orchestrator.createPackage(
                        request.getContent(),
                        category,
                        priority,
                        complexity,
                        mode,
                        userId,
                        traceId,
                        strategyOverrides
                );

                turn.setPackageId(pkg.getPackageId());
                turnRepository.save(turn);
                redisService.updateContextField(pkg.getPackageId(), "sessionId", sessionId);
                redisService.updateContextField(pkg.getPackageId(), "turnId", turnId);
                redisService.updateContextField(pkg.getPackageId(), "executionProfile", executionProfile);
                redisService.updateContextField(pkg.getPackageId(), "workloadType", workloadType);
                redisService.updateContextField(pkg.getPackageId(), "failurePolicy", failurePolicy);
                redisService.updateContextField(pkg.getPackageId(), "turnPaused", false);

                // Tool step: discover model capabilities (name/type/supports) before orchestration.
                List<CollabSessionDtos.ModelCapabilityView> discoveredCaps = listModelCapabilities(
                        CollabSessionDtos.ModelCapabilityRequest.builder()
                                .taskText(request.getContent())
                                .expectedRole(null)
                                .build()
                );
                redisService.updateContextField(pkg.getPackageId(), "modelCapabilities", discoveredCaps);
                long eligibleCount = discoveredCaps.stream().filter(c -> Boolean.TRUE.equals(c.getEligible())).count();
                publish(sessionId, turnId, "MODEL_CAPABILITIES_DISCOVERED", "MODEL_CAPABILITIES_DISCOVERED",
                        "模型能力已枚举", Map.of(
                                "total", discoveredCaps.size(),
                                "eligible", eligibleCount
                        ));

                orchestrator.decompose(pkg.getPackageId(), List.of("research", "generation", "review"));
                publish(sessionId, turnId, "DRAFT_BRANCH_STARTED", "DRAFT_BRANCH_STARTED", "并行分支已启动", Map.of(
                        "executionProfile", executionProfile,
                        "workloadType", workloadType,
                        "failurePolicy", failurePolicy
                ));

                AtomicInteger completedCounter = new AtomicInteger(0);
                int safetyGuard = 0;
                while (safetyGuard++ < 20) {
                    waitIfPaused(pkg.getPackageId(), sessionId, turnId);
                    List<CollabSubtaskEntity> toRun = orchestrator.autoScheduleIfPossible(pkg.getPackageId());
                    if (toRun.isEmpty()) {
                        break;
                    }

                    List<CompletableFuture<Void>> running = new ArrayList<>();
                    for (CollabSubtaskEntity subtask : toRun) {
                        publish(sessionId, turnId, "DRAFT_BRANCH_STARTED", "DRAFT_BRANCH_STARTED",
                                "分支开始执行: " + subtask.getSubTaskId(), Map.of(
                                        "branchId", subtask.getSubTaskId(),
                                        "subTaskId", subtask.getSubTaskId(),
                                        "expectedRole", subtask.getExpectedRole()
                                ));
                        CompletableFuture<Void> f = executor.executeSubtask(subtask, pkg.getPackageId(), traceId)
                                .handle((result, ex) -> {
                                    if (ex == null) {
                                        handleBranchCompleted(pkg.getPackageId(), sessionId, turnId, subtask, result, completedCounter);
                                        return null;
                                    }
                                    String errorText = extractThrowableMessage(ex);
                                    if ("AUTO_DEGRADE_CONTINUE".equalsIgnoreCase(failurePolicy)) {
                                        publish(sessionId, turnId, "BRANCH_DEGRADED", "BRANCH_DEGRADED",
                                                "分支降级: " + subtask.getSubTaskId(), Map.of(
                                                        "branchId", subtask.getSubTaskId(),
                                                        "subTaskId", subtask.getSubTaskId(),
                                                        "reason", defaultString(errorText, "unknown"),
                                                        "suggestedAction", "重试分支或切换主策略"
                                                ));
                                        String fallbackResult = executeFallbackBranch(subtask, pkg.getPackageId(), traceId);
                                        if (fallbackResult != null && !fallbackResult.isBlank()) {
                                            handleBranchCompleted(pkg.getPackageId(), sessionId, turnId, subtask, fallbackResult, completedCounter);
                                            return null;
                                        }
                                    }
                                    orchestrator.updateSubtaskStatus(pkg.getPackageId(), subtask.getSubTaskId(), "FAILED", null, errorText);
                                    publish(sessionId, turnId, "TURN_FAILED", "TURN_FAILED", "子任务执行失败: " + subtask.getSubTaskId(), Map.of(
                                            "subTaskId", subtask.getSubTaskId(),
                                            "branchId", subtask.getSubTaskId(),
                                            "error", errorText,
                                            "suggestedAction", "重试分支"
                                    ));
                                    return null;
                                });
                        running.add(f);
                    }
                    CompletableFuture.allOf(running.toArray(new CompletableFuture[0])).join();
                }

                List<CollabSubtaskEntity> subtasks = orchestrator.getSubtasks(pkg.getPackageId());
                long completedCount = subtasks.stream().filter(s -> "COMPLETED".equalsIgnoreCase(s.getStatus())).count();
                if (completedCount == 0) {
                    orchestrator.fail(pkg.getPackageId(), "Subtasks failed in collaborative turn");
                    turn.setStatus("FAILED");
                    turn.setErrorMessage("Subtasks failed");
                    turn.setCompletedAt(LocalDateTime.now());
                    turnRepository.save(turn);
                    executor.clearPackageAgentAssignments(pkg.getPackageId());
                    publish(sessionId, turnId, "TURN_FAILED", "TURN_FAILED", "回合失败", Map.of(
                            "suggestedAction", "调整策略或重试任务"
                    ));
                    streamService.complete(sessionId, turnId);
                    return;
                }

                publish(sessionId, turnId, "CROSS_CRITIQUE_COMPLETED", "CROSS_CRITIQUE_COMPLETED", "交叉评审完成", Map.of(
                        "completedBranches", completedCount
                ));
                List<AgentRuntimeState> branchStates = toBranchRuntimeStates(orchestrator.getSubtasks(pkg.getPackageId()));
                CollaborationArbiterService.ArbiterDecision decision = arbiterService.arbitrate(branchStates);
                publish(sessionId, turnId, "ARBITER_DECISION", "ARBITER_DECISION", "仲裁完成", Map.of(
                        "winnerBranchId", decision.getWinnerBranchId(),
                        "winnerScore", decision.getWinnerScore(),
                        "scoreboard", decision.getScoreboard()
                ));

                String finalAnswer = buildFinalAnswer(pkg.getPackageId());
                orchestrator.complete(pkg.getPackageId(), finalAnswer);

                turn.setStatus("COMPLETED");
                turn.setCompletedAt(LocalDateTime.now());
                try {
                    Map<String, Object> runtime = orchestrator.getRuntimeStatus(pkg.getPackageId());
                    Object selectionObj = runtime.getOrDefault("selection", Map.of());
                    turn.setSelectionMeta(toJson(selectionObj));
                    if (selectionObj instanceof Map<?, ?> selectionMap) {
                        Object selectionModeValue = selectionMap.get("mode");
                        if (selectionModeValue == null) {
                            selectionModeValue = selectionMap.get("selectionMode");
                        }
                        if (selectionModeValue != null && "bid".equalsIgnoreCase(String.valueOf(selectionModeValue))) {
                            publish(sessionId, turnId, "BIDDING_TRIGGERED", "BIDDING_TRIGGERED", "静态主 Agent 不可用，已触发动态竞标", Map.of(
                                    "selection", selectionMap
                            ));
                        }
                    }
                } catch (Exception ignored) {
                }
                turnRepository.save(turn);

                saveMessage(sessionId, turnId, "assistant", "FINAL_ANSWER", finalAnswer, Map.of());
                publish(sessionId, turnId, "FINAL_ANSWER", "FINAL_ANSWER", finalAnswer, Map.of(
                        "packageId", pkg.getPackageId(),
                        "arbiterWinnerBranchId", decision.getWinnerBranchId(),
                        "arbiterWinnerScore", decision.getWinnerScore()
                ));
                executor.clearPackageAgentAssignments(pkg.getPackageId());
                streamService.complete(sessionId, turnId);
            } catch (Exception e) {
                log.error("Failed to process collaborative turn: sessionId={}, turnId={}", sessionId, turnId, e);
                turn.setStatus("FAILED");
                turn.setErrorMessage(e.getMessage());
                turn.setCompletedAt(LocalDateTime.now());
                turnRepository.save(turn);
                if (turn.getPackageId() != null && !turn.getPackageId().isBlank()) {
                    executor.clearPackageAgentAssignments(turn.getPackageId());
                }
                publish(sessionId, turnId, "TURN_FAILED", "TURN_FAILED", "回合执行异常", Map.of("error", e.getMessage()));
                streamService.complete(sessionId, turnId);
            }
        });
    }

    private void handleBranchCompleted(String packageId,
                                       String sessionId,
                                       String turnId,
                                       CollabSubtaskEntity subtask,
                                       String result,
                                       AtomicInteger completedCounter) {
        orchestrator.updateSubtaskStatus(packageId, subtask.getSubTaskId(), "COMPLETED", result, null);
        Map<String, Object> scoreBreakdown = estimateScoreBreakdown(result);
        List<String> evidenceRefs = extractEvidenceRefs(result, subtask.getSubTaskId());
        redisService.updateContextField(packageId, "branchScore:" + subtask.getSubTaskId(), scoreBreakdown);
        redisService.updateContextField(packageId, "branchEvidence:" + subtask.getSubTaskId(), evidenceRefs);
        String stage = mapStage(subtask.getExpectedRole(), completedCounter.incrementAndGet());
        Map<String, Object> parsedResult = parseBranchResult(result);
        publish(sessionId, turnId, stage, stage, String.valueOf(parsedResult.getOrDefault("summaryText", truncate(result))), Map.of(
                "subTaskId", subtask.getSubTaskId(),
                "branchId", subtask.getSubTaskId(),
                "attemptId", (subtask.getRetryCount() == null ? 0 : subtask.getRetryCount()) + 1,
                "expectedRole", subtask.getExpectedRole(),
                "scoreBreakdown", scoreBreakdown,
                "evidenceRefs", evidenceRefs,
                "fallbackTrail", List.of("primary"),
                "imageUrl", parsedResult.getOrDefault("imageUrl", ""),
                "fileId", parsedResult.getOrDefault("fileId", "")
        ));
        publish(sessionId, turnId, "DRAFT_BRANCH_COMPLETED", "DRAFT_BRANCH_COMPLETED", "分支已完成: " + subtask.getSubTaskId(), Map.of(
                "subTaskId", subtask.getSubTaskId(),
                "branchId", subtask.getSubTaskId(),
                "scoreBreakdown", scoreBreakdown,
                "evidenceRefs", evidenceRefs
        ));
    }

    private String executeFallbackBranch(CollabSubtaskEntity subtask, String packageId, String traceId) {
        try {
            var agents = executor.getAvailableAgents();
            if (agents == null || agents.isEmpty()) {
                return null;
            }
            var fallbackAgent = pickFallbackAgent(subtask, agents);
            if (fallbackAgent == null) {
                return null;
            }
            String fallbackAgentId = fallbackAgent.getAgentId();
            Map<String, Object> fallbackSelection = new HashMap<>();
            fallbackSelection.put("selectedAgentId", fallbackAgentId);
            fallbackSelection.put("selectedAgentModel", fallbackAgent.getModelName() != null ? fallbackAgent.getModelName() : "");
            fallbackSelection.put("selectionMode", "fallback");
            fallbackSelection.put("mode", "fallback");
            fallbackSelection.put("selectionReason", "auto_degrade_continue_first_available");
            fallbackSelection.put("timestamp", System.currentTimeMillis());
            memoryService.writeToBlackboard(packageId, "selection_" + subtask.getSubTaskId(), fallbackSelection);
            memoryService.writeToBlackboard(packageId, "selection_last", fallbackSelection);
            String role = subtask.getExpectedRole() == null ? "SPECIALIST" : subtask.getExpectedRole().toUpperCase(Locale.ROOT);
            boolean imageExecutionStep = "SPECIALIST".equals(role)
                    && subtask.getDescription() != null
                    && List.of("执行图像生成", "生成图像", "出图", "draw", "generate image", "text to image").stream()
                    .anyMatch(k -> subtask.getDescription().toLowerCase(Locale.ROOT).contains(k.toLowerCase(Locale.ROOT)));
            String hardenedPrompt = """
                    [Fallback execution]
                    请直接给出可执行结果，不要输出任何工具调用建议、命令建议、或 <tool_call> 标签。
                    不要偏题到代码审查/系统性能报告/安全审计模板。
                    原始任务：
                    """ + subtask.getDescription()
                    + (imageExecutionStep
                    ? "\n补充要求：这是执行出图步骤，优先返回 image_url/file_id。"
                    : "\n补充要求：这是非出图执行步骤，仅返回文本结论，不要返回 image_url JSON。");
            return executor.executeWithSpecificAgent(
                            subtask.getSubTaskId() + "_fallback",
                            fallbackAgentId,
                            hardenedPrompt,
                            packageId,
                            traceId)
                    .get(45, TimeUnit.SECONDS);
        } catch (Exception e) {
            return null;
        }
    }

    private AgentMetadata pickFallbackAgent(CollabSubtaskEntity subtask, List<AgentMetadata> agents) {
        if (agents == null || agents.isEmpty()) {
            return null;
        }
        String desc = subtask != null && subtask.getDescription() != null
                ? subtask.getDescription().toLowerCase(java.util.Locale.ROOT)
                : "";
        boolean imageIntent = List.of(
                "image", "photo", "picture", "poster", "thumbnail", "illustration", "draw",
                "图片", "照片", "海报", "封面", "插画", "绘图", "画图", "出图", "生成图",
                "画一幅", "画个", "画一张", "绘制", "作画", "文生图", "绘画", "画作", "图像创作"
        ).stream().anyMatch(desc::contains);
        boolean requireImageAgent = shouldUseImageAgentForFallback(subtask, imageIntent);
        if (requireImageAgent) {
            for (AgentMetadata agent : agents) {
                if (agent == null) {
                    continue;
                }
                String merged = ((agent.getViewType() != null ? agent.getViewType() : "") + " "
                        + (agent.getMode() != null ? agent.getMode() : "") + " "
                        + (agent.getModelName() != null ? agent.getModelName() : "") + " "
                        + (agent.getDescription() != null ? agent.getDescription() : ""))
                        .toLowerCase(java.util.Locale.ROOT);
                if (merged.contains("tti")
                        || merged.contains("image")
                        || merged.contains("vision")
                        || merged.contains("dalle")
                        || merged.contains("sdxl")
                        || merged.contains("diffusion")
                        || merged.contains("flux")
                        || merged.contains("绘图")
                        || merged.contains("图片")) {
                    return agent;
                }
            }
            return null;
        }
        // 图像任务中的非执行分支（规划/评审/质检/协调）和普通文本任务都优先 CHAT 视图类型。
        List<AgentMetadata> chatAgents = agents.stream()
                .filter(a -> a != null && (a.getViewType() == null || "CHAT".equalsIgnoreCase(a.getViewType())))
                .sorted(java.util.Comparator.comparing(AgentMetadata::getAgentId, java.util.Comparator.nullsLast(String::compareTo)))
                .toList();
        List<AgentMetadata> pool = chatAgents.isEmpty()
                ? agents.stream().filter(java.util.Objects::nonNull).toList()
                : chatAgents;
        if (pool.isEmpty()) {
            return null;
        }
        int idx = Math.floorMod(java.util.Objects.hash(
                subtask != null ? subtask.getSubTaskId() : "",
                subtask != null ? subtask.getExpectedRole() : ""
        ), pool.size());
        return pool.get(idx);
    }

    private boolean shouldUseImageAgentForFallback(CollabSubtaskEntity subtask, boolean imageIntent) {
        if (!imageIntent) {
            return false;
        }
        String role = subtask != null && subtask.getExpectedRole() != null
                ? subtask.getExpectedRole().toUpperCase(Locale.ROOT)
                : "";
        if (TEXT_ONLY_ROLES_IN_IMAGE_TASK.contains(role)) {
            return false;
        }
        String description = subtask != null && subtask.getDescription() != null
                ? subtask.getDescription().toLowerCase(Locale.ROOT)
                : "";
        return IMAGE_EXECUTION_KEYWORDS.stream().anyMatch(k -> description.contains(k.toLowerCase(Locale.ROOT)));
    }

    private String extractThrowableMessage(Throwable ex) {
        if (ex == null) {
            return "unknown";
        }
        Throwable cur = ex;
        while ((cur instanceof java.util.concurrent.CompletionException
                || cur instanceof java.util.concurrent.ExecutionException)
                && cur.getCause() != null) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        if (msg == null || msg.isBlank()) {
            return cur.getClass().getSimpleName();
        }
        return msg;
    }

    private void waitIfPaused(String packageId, String sessionId, String turnId) {
        boolean notified = false;
        for (int i = 0; i < 120; i++) {
            boolean paused = redisService.getContextField(packageId, "turnPaused")
                    .map(v -> Boolean.parseBoolean(String.valueOf(v)))
                    .orElse(false);
            if (!paused) {
                return;
            }
            if (!notified) {
                publish(sessionId, turnId, "ROUND_GUARDRAIL_TRIGGERED", "ROUND_GUARDRAIL_TRIGGERED",
                        "回合暂停中，等待恢复", Map.of("suggestedAction", "点击恢复继续执行"));
                notified = true;
            }
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private List<AgentRuntimeState> toBranchRuntimeStates(List<CollabSubtaskEntity> subtasks) {
        if (subtasks == null) {
            return List.of();
        }
        return subtasks.stream()
                .filter(s -> s.getResult() != null && !s.getResult().isBlank())
                .map(s -> {
                    List<String> evidence = redisService.getContextField(s.getPackageId(), "branchEvidence:" + s.getSubTaskId())
                            .map(v -> {
                                if (v instanceof List<?> values) {
                                    return values.stream().map(String::valueOf).toList();
                                }
                                return List.<String>of();
                            })
                            .orElse(List.of());
                    @SuppressWarnings("unchecked")
                    Map<String, Object> score = (Map<String, Object>) redisService.getContextField(s.getPackageId(),
                            "branchScore:" + s.getSubTaskId()).orElse(Map.of());

                    return AgentRuntimeState.builder()
                            .branchId(s.getSubTaskId())
                            .role(s.getExpectedRole())
                            .status(s.getStatus())
                            .attemptId((s.getRetryCount() == null ? 0 : s.getRetryCount()) + 1)
                            .summary(truncate(s.getResult()))
                            .evidenceRefs(evidence)
                            .scoreBreakdown(score)
                            .fallbackTrail(List.of("primary"))
                            .build();
                })
                .toList();
    }

    private Map<String, Object> estimateScoreBreakdown(String result) {
        int len = result == null ? 0 : result.length();
        double correctness = len > 500 ? 0.86 : (len > 200 ? 0.74 : 0.58);
        double evidence = result != null && result.contains("http") ? 0.8 : 0.55;
        double testability = result != null && (result.contains("测试") || result.toLowerCase().contains("test")) ? 0.85 : 0.6;
        double cost = len <= 600 ? 1.0 : (len <= 1200 ? 0.8 : 0.55);
        double weighted = correctness * 0.45 + evidence * 0.25 + testability * 0.2 + cost * 0.1;
        return Map.of(
                "correctness", round(correctness),
                "evidence", round(evidence),
                "testability", round(testability),
                "cost", round(cost),
                "weightedTotal", round(weighted)
        );
    }

    private List<String> extractEvidenceRefs(String result, String branchId) {
        List<String> refs = new ArrayList<>();
        if (result == null || result.isBlank()) {
            return refs;
        }
        if (result.contains("http://") || result.contains("https://")) {
            refs.add("url:" + branchId + ":1");
        }
        String[] lines = result.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("-") || trimmed.startsWith("1.") || trimmed.startsWith("*")) {
                refs.add("claim:" + branchId + ":" + (refs.size() + 1));
            }
            if (refs.size() >= 3) {
                break;
            }
        }
        if (refs.isEmpty()) {
            refs.add("claim:" + branchId + ":1");
        }
        return refs;
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private String buildFinalAnswer(String packageId) {
        List<CollabSubtaskEntity> subtasks = orchestrator.getSubtasks(packageId);
        StringBuilder builder = new StringBuilder();
        subtasks.stream()
                .filter(s -> s.getResult() != null && !s.getResult().isBlank())
                .forEach(s -> {
                    Map<String, Object> parsed = parseBranchResult(s.getResult());
                    String summary = String.valueOf(parsed.getOrDefault("summaryText", truncate(s.getResult())));
                    builder.append("[" + s.getExpectedRole() + "] ")
                            .append(summary)
                            .append("\n\n");
                });
        if (builder.length() == 0) {
            return "协作已完成，但未产出可展示内容。";
        }
        return builder.toString().trim();
    }

    private String mapStage(String expectedRole, int completedCounter) {
        if ("REVIEWER".equalsIgnoreCase(expectedRole) || "CRITIC".equalsIgnoreCase(expectedRole)) {
            return "CRITIQUE_ROUND";
        }
        if (completedCounter == 1) {
            return "RESEARCH_COMPLETED";
        }
        if (completedCounter == 2) {
            return "DRAFT_BRANCH_COMPLETED";
        }
        return "MERGE_COMPLETED";
    }

    private void publish(String sessionId,
                         String turnId,
                         String eventType,
                         String stage,
                         String content,
                         Map<String, Object> data) {
        saveMessage(sessionId, turnId, "system", eventType, content, data);
        streamService.publish(CollabSessionDtos.StreamEvent.builder()
                .eventType(eventType)
                .sessionId(sessionId)
                .turnId(turnId)
                .stage(stage)
                .content(content)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build());
    }

    private void saveMessage(String sessionId,
                             String turnId,
                             String role,
                             String stage,
                             String content,
                             Map<String, Object> metadata) {
        messageRepository.save(CollabMessageEntity.builder()
                .sessionId(sessionId)
                .turnId(turnId)
                .role(role)
                .stage(stage)
                .content(content)
                .metadata(toJson(metadata))
                .build());
    }

    private CollabSessionDtos.SessionView toSessionView(CollabSessionEntity entity) {
        return CollabSessionDtos.SessionView.builder()
                .sessionId(entity.getSessionId())
                .title(entity.getTitle())
                .status(entity.getStatus())
                .mainAgentPolicy(entity.getMainAgentPolicy())
                .qualityThreshold(entity.getQualityThreshold())
                .maxCritiqueRounds(entity.getMaxCritiqueRounds())
                .draftParallelism(entity.getDraftParallelism())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private CollabSessionDtos.MessageView toMessageView(CollabMessageEntity entity) {
        return CollabSessionDtos.MessageView.builder()
                .id(entity.getId())
                .sessionId(entity.getSessionId())
                .turnId(entity.getTurnId())
                .role(entity.getRole())
                .stage(entity.getStage())
                .content(entity.getContent())
                .metadata(readMapJson(entity.getMetadata()))
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private List<Map<String, Object>> buildTimeline(String sessionId, String turnId) {
        return messageRepository.findBySessionIdAndTurnIdOrderByCreatedAtAsc(sessionId, turnId, PageRequest.of(0, 500))
                .stream()
                .filter(m -> m.getStage() != null && !m.getStage().isBlank())
                .map(m -> Map.<String, Object>of(
                        "stage", defaultString(m.getStage(), "UNKNOWN"),
                        "content", defaultString(m.getContent(), ""),
                        "timestamp", m.getCreatedAt() != null ? m.getCreatedAt().toString() : "",
                        "data", readMapJson(m.getMetadata())
                ))
                .toList();
    }

    private List<Map<String, Object>> buildBranches(String packageId) {
        Map<String, Map<String, Object>> eventSelectionIndex = buildSelectionIndexFromEventLogs(packageId);
        return orchestrator.getSubtasks(packageId).stream()
                .map(s -> {
                    Map<String, Object> score = readMapFromContext(packageId, "branchScore:" + s.getSubTaskId());
                    List<String> evidenceRefs = readListFromContext(packageId, "branchEvidence:" + s.getSubTaskId());
                    Map<String, Object> selection = readSelectionFromBlackboard(packageId, s.getSubTaskId());
                    if (selection.isEmpty()) {
                        selection = eventSelectionIndex.getOrDefault(s.getSubTaskId(), Map.of());
                    }
                    String selectedAgentId = defaultString(asString(selection.get("selectedAgentId")), "");
                    String selectedAgentModel = defaultString(asString(selection.get("selectedAgentModel")), "");
                    String selectionMode = defaultString(asString(selection.get("mode")), defaultString(asString(selection.get("selectionMode")), ""));
                    String selectionReason = defaultString(asString(selection.get("selectionReason")), "");
                    String degradeReason = "FAILED".equalsIgnoreCase(s.getStatus()) ? defaultString(s.getErrorMessage(), "unknown") : "";
                    Map<String, Object> branch = new HashMap<>();
                    branch.put("branchId", s.getSubTaskId());
                    branch.put("subTaskId", s.getSubTaskId());
                    branch.put("role", defaultString(s.getExpectedRole(), "SPECIALIST"));
                    branch.put("status", defaultString(s.getStatus(), "PENDING"));
                    branch.put("selectedAgentId", selectedAgentId);
                    branch.put("selectedAgentModel", selectedAgentModel);
                    branch.put("selectionMode", selectionMode);
                    branch.put("selectionReason", selectionReason);
                    branch.put("scoreBreakdown", score);
                    branch.put("score", score.getOrDefault("weightedTotal", 0.0));
                    branch.put("degradeReason", degradeReason);
                    Map<String, Object> parsedResult = parseBranchResult(s.getResult());
                    branch.put("summary", parsedResult.getOrDefault("summary", "-"));
                    branch.put("summaryText", parsedResult.getOrDefault("summaryText", "-"));
                    branch.put("imageUrl", parsedResult.getOrDefault("imageUrl", ""));
                    branch.put("fileId", parsedResult.getOrDefault("fileId", ""));
                    branch.put("rawResult", truncate(s.getResult()));
                    branch.put("evidenceRefs", evidenceRefs);
                    branch.put("fallbackTrail", List.of("primary"));
                    return branch;
                })
                .collect(Collectors.toList());
    }

    private Map<String, Map<String, Object>> buildSelectionIndexFromEventLogs(String packageId) {
        Map<String, Map<String, Object>> index = new HashMap<>();
        if (packageId == null || packageId.isBlank()) {
            return index;
        }
        eventLogRepository.findByPackageIdOrderByCreatedAtAsc(packageId)
                .forEach(log -> {
                    String subTaskId = defaultString(log.getSubTaskId(), "");
                    if (subTaskId.isBlank()) {
                        return;
                    }
                    Map<String, Object> eventData = readMapJson(log.getEventData());
                    Map<String, Object> payload = new HashMap<>();
                    String agentId = defaultString(log.getAgentId(), defaultString(asString(eventData.get("selectedAgentId")), ""));
                    if (!agentId.isBlank()) {
                        payload.put("selectedAgentId", agentId);
                    }
                    String model = defaultString(asString(eventData.get("selectedAgentModel")), defaultString(asString(eventData.get("agentModel")), ""));
                    if (!model.isBlank()) {
                        payload.put("selectedAgentModel", model);
                    }
                    String mode = defaultString(asString(eventData.get("selectionMode")), defaultString(asString(eventData.get("mode")), ""));
                    if (!mode.isBlank()) {
                        payload.put("selectionMode", mode);
                        payload.put("mode", mode);
                    }
                    String reason = defaultString(asString(eventData.get("selectionReason")), "");
                    if (!reason.isBlank()) {
                        payload.put("selectionReason", reason);
                    }
                    if (!payload.isEmpty()) {
                        index.merge(subTaskId, payload, (oldVal, newVal) -> {
                            oldVal.putAll(newVal);
                            return oldVal;
                        });
                    }
                });
        return index;
    }

    private Map<String, Object> buildArbiterSnapshot(String sessionId, String turnId) {
        List<CollabMessageEntity> events = messageRepository.findBySessionIdAndTurnIdOrderByCreatedAtAsc(
                sessionId, turnId, PageRequest.of(0, 500)).getContent();
        for (int i = events.size() - 1; i >= 0; i--) {
            CollabMessageEntity event = events.get(i);
            if ("ARBITER_DECISION".equalsIgnoreCase(event.getStage())) {
                return readMapJson(event.getMetadata());
            }
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readSelectionFromBlackboard(String packageId, String subTaskId) {
        if (packageId == null || packageId.isBlank() || subTaskId == null || subTaskId.isBlank()) {
            return Map.of();
        }
        return memoryService.readFromBlackboard(packageId, "selection_" + subTaskId)
                .filter(Map.class::isInstance)
                .map(v -> (Map<String, Object>) v)
                .orElse(Map.of());
    }

    private Map<String, Object> inferLatestSelectionFromBranches(List<Map<String, Object>> branches) {
        if (branches == null || branches.isEmpty()) {
            return Map.of();
        }
        for (int i = branches.size() - 1; i >= 0; i--) {
            Map<String, Object> branch = branches.get(i);
            String agentId = String.valueOf(branch.getOrDefault("selectedAgentId", ""));
            if (agentId != null && !agentId.isBlank()) {
                return Map.of(
                        "selectedAgentId", agentId,
                        "selectedAgentModel", String.valueOf(branch.getOrDefault("selectedAgentModel", "")),
                        "mode", String.valueOf(branch.getOrDefault("selectionMode", "")),
                        "selectionReason", String.valueOf(branch.getOrDefault("selectionReason", ""))
                );
            }
        }
        return Map.of();
    }

    private List<Map<String, Object>> collectEvidenceRefs(List<Map<String, Object>> branches) {
        List<Map<String, Object>> refs = new ArrayList<>();
        for (Map<String, Object> branch : branches) {
            Object branchId = branch.get("branchId");
            Object evidenceObj = branch.get("evidenceRefs");
            if (!(evidenceObj instanceof List<?> list)) {
                continue;
            }
            for (Object ref : list) {
                refs.add(Map.of(
                        "branchId", branchId != null ? String.valueOf(branchId) : "",
                        "ref", String.valueOf(ref)
                ));
            }
        }
        return refs;
    }

    private Map<String, Boolean> buildUiActions(String packageId, String latestTurnStatus, List<Map<String, Object>> branches) {
        if (packageId == null || packageId.isBlank()) {
            return defaultUiActions(false, false, false);
        }
        boolean paused = redisService.getContextField(packageId, "turnPaused")
                .map(v -> Boolean.parseBoolean(String.valueOf(v)))
                .orElse(false);
        boolean hasRetryableBranch = branches.stream()
                .anyMatch(b -> "FAILED".equalsIgnoreCase(String.valueOf(b.get("status")))
                        || "BRANCH_DEGRADED".equalsIgnoreCase(String.valueOf(b.get("status"))));
        boolean running = !"COMPLETED".equalsIgnoreCase(latestTurnStatus) && !"FAILED".equalsIgnoreCase(latestTurnStatus);
        return defaultUiActions(running, paused, hasRetryableBranch);
    }

    private Map<String, Boolean> defaultUiActions(boolean running, boolean paused, boolean hasRetryableBranch) {
        return Map.of(
                "canPause", running && !paused,
                "canResume", running && paused,
                "canRetryBranch", hasRetryableBranch,
                "canSwitchPolicy", running
        );
    }

    private List<String> buildOperatorHints(String packageId, List<Map<String, Object>> branches, String latestTurnStatus) {
        List<String> hints = new ArrayList<>();
        long failed = branches.stream()
                .filter(b -> "FAILED".equalsIgnoreCase(String.valueOf(b.get("status"))))
                .count();
        if (failed > 0) {
            hints.add("检测到失败分支，建议先重试分支或切换主策略。");
        }
        boolean degraded = branches.stream()
                .anyMatch(b -> {
                    Object reason = b.get("degradeReason");
                    return reason != null && !String.valueOf(reason).isBlank();
                });
        if (degraded) {
            hints.add("部分分支发生降级，建议关注仲裁评分与证据引用。");
        }
        if ("RUNNING".equalsIgnoreCase(latestTurnStatus)) {
            hints.add("回合执行中，可随时暂停并观察分支状态。");
        }
        if (isImageIntent(packageId) && !hasImageAgentRouting(branches)) {
            hints.add("检测到图片/照片生成意图，但当前未命中图像Agent，建议配置 mainAgentStaticDefault 或 bidWhitelist。");
        }
        if (hints.isEmpty()) {
            hints.add("当前运行稳定，可继续观察仲裁结果。");
        }
        return hints;
    }

    private boolean isImageIntent(String packageId) {
        if (packageId == null || packageId.isBlank()) {
            return false;
        }
        String intent = packageRepository.findByPackageId(packageId)
                .map(CollaborationPackageEntity::getIntent)
                .orElse("");
        if (intent == null || intent.isBlank()) {
            return false;
        }
        String text = intent.toLowerCase();
        return text.contains("image") || text.contains("photo") || text.contains("picture")
                || text.contains("图片") || text.contains("照片") || text.contains("海报")
                || text.contains("插画") || text.contains("生成图");
    }

    private boolean hasImageAgentRouting(List<Map<String, Object>> branches) {
        if (branches == null || branches.isEmpty()) {
            return false;
        }
        for (Map<String, Object> branch : branches) {
            String agentId = String.valueOf(branch.getOrDefault("selectedAgentId", "")).toLowerCase();
            String model = String.valueOf(branch.getOrDefault("selectedAgentModel", "")).toLowerCase();
            String reason = String.valueOf(branch.getOrDefault("selectionReason", "")).toLowerCase();
            String merged = agentId + " " + model + " " + reason;
            if (merged.contains("image") || merged.contains("vision") || merged.contains("photo")
                    || merged.contains("dalle") || merged.contains("diffusion")
                    || merged.contains("图片") || merged.contains("视觉") || merged.contains("绘图")) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMapFromContext(String packageId, String key) {
        Object value = redisService.getContextField(packageId, key).orElse(Map.of());
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private List<String> readListFromContext(String packageId, String key) {
        Object value = redisService.getContextField(packageId, key).orElse(List.of());
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private String defaultString(String value, String def) {
        return value == null || value.isBlank() ? def : value;
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, Object> readMapJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private List<String> readStringListJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String truncate(String input) {
        if (input == null) {
            return "";
        }
        return input.length() > 400 ? input.substring(0, 400) + "..." : input;
    }

    private Map<String, Object> parseBranchResult(String result) {
        if (result == null || result.isBlank()) {
            return Map.of("summary", "-", "summaryText", "-", "imageUrl", "", "fileId", "");
        }
        String text = result.trim();
        try {
            JsonNode root = objectMapper.readTree(text);
            JsonNode data = root.path("data");
            String imageUrl = firstNonBlank(
                    data.path("image_url").asText(""),
                    root.path("image_url").asText("")
            );
            String fileId = firstNonBlank(
                    data.path("file_id").asText(""),
                    root.path("file_id").asText("")
            );
            String prompt = firstNonBlank(
                    data.path("prompt").asText(""),
                    root.path("prompt").asText("")
            );
            if (!imageUrl.isBlank() || !fileId.isBlank()) {
                StringBuilder summary = new StringBuilder("已生成图片");
                if (!fileId.isBlank()) {
                    summary.append(" (file_id=").append(fileId).append(")");
                }
                if (!prompt.isBlank()) {
                    summary.append("\nPrompt: ").append(truncate(prompt));
                }
                return Map.of(
                        "summary", summary.toString(),
                        "summaryText", summary.toString(),
                        "imageUrl", imageUrl,
                        "fileId", fileId
                );
            }
        } catch (Exception ignored) {
        }
        return Map.of(
                "summary", truncate(text),
                "summaryText", truncate(text),
                "imageUrl", "",
                "fileId", ""
        );
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private double computeP95(List<Long> sortedValues) {
        if (sortedValues == null || sortedValues.isEmpty()) {
            return 0.0;
        }
        int index = (int) Math.ceil(sortedValues.size() * 0.95) - 1;
        int safeIndex = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(safeIndex);
    }

    private boolean isBiddingTriggered(CollabTurnEntity turn) {
        if (turn.getSelectionMeta() == null || turn.getSelectionMeta().isBlank()) {
            return false;
        }
        Map<String, Object> selection = readMapJson(turn.getSelectionMeta());
        Object mode = selection.get("mode");
        if (mode == null) {
            mode = selection.get("selectionMode");
        }
        if (mode == null) {
            return turn.getSelectionMeta().contains("\"mode\":\"bid\"")
                    || turn.getSelectionMeta().contains("\"selectionMode\":\"bid\"");
        }
        return "bid".equalsIgnoreCase(String.valueOf(mode));
    }

    private long getDlqBacklog() {
        try {
            Properties props = amqpAdmin.getQueueProperties(collaborationDlqName);
            if (props == null) {
                return 0L;
            }
            Object value = props.getProperty("QUEUE_MESSAGE_COUNT");
            if (value == null) {
                value = props.get("QUEUE_MESSAGE_COUNT");
            }
            if (value == null) {
                value = props.getProperty("messageCount");
            }
            if (value == null) {
                value = props.get("messageCount");
            }
            if (value instanceof Number n) {
                return n.longValue();
            }
            if (value != null) {
                return Long.parseLong(String.valueOf(value));
            }
        } catch (Exception e) {
            log.warn("Failed to read collaboration DLQ backlog: {}", collaborationDlqName, e);
        }
        return 0L;
    }

    private String levelForDirect(double value, double warnThreshold, double criticalThreshold) {
        if (value >= criticalThreshold) {
            return "RED";
        }
        if (value >= warnThreshold) {
            return "YELLOW";
        }
        return "GREEN";
    }

    private String levelForDirect(long value, long warnThreshold, long criticalThreshold) {
        if (value >= criticalThreshold) {
            return "RED";
        }
        if (value >= warnThreshold) {
            return "YELLOW";
        }
        return "GREEN";
    }

    private String levelForInverse(double value, double warnThreshold, double criticalThreshold) {
        if (value <= criticalThreshold) {
            return "RED";
        }
        if (value <= warnThreshold) {
            return "YELLOW";
        }
        return "GREEN";
    }

    private String computeOverallLevel(Map<String, String> metricLevels) {
        if (metricLevels.values().stream().anyMatch("RED"::equals)) {
            return "RED";
        }
        if (metricLevels.values().stream().anyMatch("YELLOW"::equals)) {
            return "YELLOW";
        }
        return "GREEN";
    }

    private String formatPercent(double value) {
        return String.format("%.1f%%", value * 100.0);
    }
}
