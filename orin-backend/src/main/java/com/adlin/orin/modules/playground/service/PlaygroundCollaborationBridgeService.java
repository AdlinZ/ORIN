package com.adlin.orin.modules.playground.service;

import com.adlin.orin.modules.collaboration.dto.CollaborationPackage;
import com.adlin.orin.modules.collaboration.entity.CollabSubtaskEntity;
import com.adlin.orin.modules.collaboration.entity.CollaborationPackageEntity;
import com.adlin.orin.modules.collaboration.repository.CollabSubtaskRepository;
import com.adlin.orin.modules.collaboration.repository.CollaborationPackageRepository;
import com.adlin.orin.modules.collaboration.service.CollaborationExecutor;
import com.adlin.orin.modules.collaboration.service.CollaborationOrchestrator;
import com.adlin.orin.modules.collaboration.service.CollaborationRedisService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaygroundCollaborationBridgeService {

    private final CollaborationOrchestrator orchestrator;
    private final CollaborationExecutor executor;
    private final CollaborationPackageRepository packageRepository;
    private final CollabSubtaskRepository subtaskRepository;
    private final CollaborationRedisService redisService;
    private final ObjectMapper objectMapper;

    @Transactional
    public Map<String, Object> bootstrap(Map<String, Object> payload, String userId, String traceId) {
        String intent = text(payload.get("intent"), "");
        String mode = text(payload.get("collaboration_mode"), "SEQUENTIAL").toUpperCase();
        String workflowType = text(payload.get("workflow_type"), "router_specialists");

        if (intent.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "intent is required");
        }

        Object subtasksObj = payload.get("subtasks");
        if (!(subtasksObj instanceof List<?> subtasksRaw) || subtasksRaw.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "subtasks is required");
        }

        CollaborationPackage pkg = orchestrator.createPackage(
                intent,
                "GENERAL",
                "NORMAL",
                subtasksRaw.size() > 2 ? "COMPLEX" : "SIMPLE",
                mode,
                userId,
                text(payload.get("trace_id"), traceId)
        );

        String packageId = pkg.getPackageId();
        Optional<CollaborationPackageEntity> entityOpt = packageRepository.findByPackageId(packageId);
        entityOpt.ifPresent(entity -> {
            entity.setStatus(CollaborationOrchestrator.STATUS_EXECUTING);
            packageRepository.save(entity);
        });

        List<CollabSubtaskEntity> entities = new ArrayList<>();
        List<Map<String, Object>> normalizedSubtasks = new ArrayList<>();

        int index = 0;
        for (Object item : subtasksRaw) {
            if (!(item instanceof Map<?, ?> itemMap)) {
                continue;
            }
            String subTaskId = text(itemMap.get("id"), "task_" + (++index));
            String description = text(itemMap.get("description"), "execute subtask " + subTaskId);
            String role = text(itemMap.get("role"), CollaborationOrchestrator.ROLE_SPECIALIST);
            List<String> dependsOn = toStringList(itemMap.get("depends_on"));

            Map<String, Object> inputData = toObjectMap(itemMap.get("input_data"));

            CollabSubtaskEntity subtask = CollabSubtaskEntity.builder()
                    .packageId(packageId)
                    .subTaskId(subTaskId)
                    .description(description)
                    .expectedRole(role)
                    .dependsOn(toJson(dependsOn))
                    .inputData(toJson(inputData))
                    .confidence(0.85)
                    .status(CollaborationOrchestrator.SUBTASK_PENDING)
                    .retryCount(0)
                    .build();
            entities.add(subtask);

            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("id", subTaskId);
            normalized.put("description", description);
            normalized.put("role", role);
            normalized.put("dependsOn", dependsOn);
            normalized.put("inputData", inputData);
            normalizedSubtasks.add(normalized);
        }

        if (entities.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "subtasks is empty after normalization");
        }

        subtaskRepository.saveAll(entities);

        Map<String, Object> context = new HashMap<>();
        context.put("sub_tasks", normalizedSubtasks);
        context.put("run_id", text(payload.get("run_id"), ""));
        context.put("workflow_type", workflowType);
        context.put("intent", intent);
        context.put("agent_max_tokens", boundedInt(payload.get("agent_max_tokens"), 2400, 256, 16000));
        context.put("turnPaused", false);
        redisService.saveContext(packageId, context);

        return Map.of(
                "package_id", packageId,
                "collaboration_mode", mode,
                "workflow_type", workflowType,
                "subtasks", normalizedSubtasks
        );
    }

    public Map<String, Object> executeSubtask(String packageId, String subTaskId, String traceId) {
        CollabSubtaskEntity subtask = subtaskRepository.findByPackageIdAndSubTaskId(packageId, subTaskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subtask not found."));

        if (!CollaborationOrchestrator.SUBTASK_PENDING.equals(subtask.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Subtask status is not PENDING, currentStatus=" + subtask.getStatus());
        }

        orchestrator.updateSubtaskStatus(packageId, subTaskId, CollaborationOrchestrator.SUBTASK_RUNNING, null, null);

        executor.executeSubtask(subtask, packageId, traceId).thenAccept(result ->
                safeUpdateSubtaskStatus(packageId, subTaskId, CollaborationOrchestrator.SUBTASK_COMPLETED, result, null)
        ).exceptionally(e -> {
            safeUpdateSubtaskStatus(packageId, subTaskId, CollaborationOrchestrator.SUBTASK_FAILED, null, e.getMessage());
            return null;
        });

        return Map.of(
                "status", "STARTED",
                "package_id", packageId,
                "sub_task_id", subTaskId
        );
    }

    private void safeUpdateSubtaskStatus(String packageId, String subTaskId, String targetStatus,
                                         String result, String errorMessage) {
        try {
            CollabSubtaskEntity latest = subtaskRepository.findByPackageIdAndSubTaskId(packageId, subTaskId).orElse(null);
            if (latest == null || !CollaborationOrchestrator.SUBTASK_RUNNING.equals(latest.getStatus())) {
                return;
            }
            orchestrator.updateSubtaskStatus(packageId, subTaskId, targetStatus, result, errorMessage);
        } catch (Exception ignored) {
            // no-op: callback/state transition races are handled by orchestrator guards
        }
    }

    private String text(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String result = String.valueOf(value).trim();
        return result.isBlank() ? fallback : result;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "null";
        }
    }

    private Map<String, Object> toObjectMap(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> map = new LinkedHashMap<>();
            source.forEach((k, v) -> map.put(String.valueOf(k), v));
            return map;
        }
        return Map.of();
    }

    private int boundedInt(Object value, int fallback, int min, int max) {
        int parsed = fallback;
        if (value instanceof Number number) {
            parsed = number.intValue();
        } else if (value != null) {
            try {
                parsed = Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                parsed = fallback;
            }
        }
        return Math.max(min, Math.min(max, parsed));
    }

    private List<String> toStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
