package com.adlin.orin.modules.playground.controller;

import com.adlin.orin.gateway.adapter.ProviderAdapter;
import com.adlin.orin.gateway.dto.ChatCompletionRequest;
import com.adlin.orin.gateway.dto.ChatCompletionResponse;
import com.adlin.orin.gateway.service.RouterService;
import com.adlin.orin.modules.playground.service.PlaygroundCollaborationBridgeService;
import com.adlin.orin.modules.playground.service.PlaygroundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/playground")
public class PlaygroundController {

    private final PlaygroundService playgroundService;
    private final PlaygroundCollaborationBridgeService collaborationBridgeService;
    private final RouterService routerService;

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/workflow-templates")
    public List<Map<String, Object>> workflowTemplates() {
        return playgroundService.templates();
    }

    @GetMapping("/settings")
    public Map<String, Object> settings() {
        return playgroundService.appSettings();
    }

    @PutMapping("/settings")
    public Map<String, Object> updateSettings(@RequestBody Map<String, Object> payload) {
        return payload;
    }

    @GetMapping("/skills")
    public List<Map<String, Object>> skills() {
        return playgroundService.skills();
    }

    @PostMapping("/skills")
    public Map<String, Object> createSkill(@RequestBody Map<String, Object> payload) {
        return playgroundService.createSkill(payload);
    }

    @PostMapping("/skills/sync")
    public Map<String, Object> syncSkills(@RequestBody Map<String, Object> payload) {
        return Map.of(
                "provider", String.valueOf(payload.getOrDefault("provider", "skillhub")),
                "query", String.valueOf(payload.getOrDefault("query", "search")),
                "fetched", 0,
                "imported", 0,
                "updated", 0
        );
    }

    @PostMapping("/skills/{skillId}/install")
    public Map<String, Object> installSkill(@PathVariable String skillId) {
        return Map.of(
                "skill_id", skillId,
                "skill_name", skillId,
                "downloaded_files", 0,
                "tool_enabled", false,
                "message", "Skill runtime installation is not required for the ORIN Playground MVP."
        );
    }

    @GetMapping("/agents")
    public List<Map<String, Object>> agents() {
        return playgroundService.listAgents();
    }

    @PostMapping("/agents")
    public Map<String, Object> createAgent(@RequestBody Map<String, Object> payload) {
        return playgroundService.createAgent(payload);
    }

    @PutMapping("/agents/{agentId}")
    public Map<String, Object> updateAgent(@PathVariable String agentId, @RequestBody Map<String, Object> payload) {
        return playgroundService.updateAgent(agentId, payload);
    }

    @DeleteMapping("/agents/{agentId}")
    public Map<String, Object> deleteAgent(@PathVariable String agentId) {
        return playgroundService.deleteAgent(agentId);
    }

    @GetMapping("/workflows")
    public List<Map<String, Object>> workflows() {
        return playgroundService.listWorkflows();
    }

    @PostMapping("/workflows")
    public Map<String, Object> createWorkflow(@RequestBody Map<String, Object> payload) {
        return playgroundService.createWorkflow(payload);
    }

    @PutMapping("/workflows/{workflowId}")
    public Map<String, Object> updateWorkflow(@PathVariable String workflowId, @RequestBody Map<String, Object> payload) {
        return playgroundService.updateWorkflow(workflowId, payload);
    }

    @DeleteMapping("/workflows/{workflowId}")
    public Map<String, Object> deleteWorkflow(@PathVariable String workflowId) {
        return playgroundService.deleteWorkflow(workflowId);
    }

    @GetMapping("/workflows/{workflowId}/graph")
    public Map<String, Object> workflowGraph(@PathVariable String workflowId) {
        return playgroundService.getWorkflowGraph(workflowId);
    }

    @PostMapping("/runs")
    public Map<String, Object> run(@RequestBody Map<String, Object> payload,
                                   @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId,
                                   @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return playgroundService.runWorkflow(payload, userId, traceId);
    }

    @PostMapping("/collaboration/bootstrap")
    public Map<String, Object> bootstrapCollaboration(@RequestBody Map<String, Object> payload,
                                                      @RequestHeader(value = "X-User-Id", defaultValue = "playground") String userId,
                                                      @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return collaborationBridgeService.bootstrap(payload, userId, traceId);
    }

    @PostMapping("/collaboration/packages/{packageId}/subtasks/{subTaskId}/execute")
    public Map<String, Object> executeCollaborationSubtask(@PathVariable String packageId,
                                                            @PathVariable String subTaskId,
                                                            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return collaborationBridgeService.executeSubtask(packageId, subTaskId, traceId);
    }

    @PostMapping(value = "/runs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter runStream(@RequestBody Map<String, Object> payload,
                                @RequestHeader(value = "X-User-Id", defaultValue = "system") String userId,
                                @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        SseEmitter emitter = new SseEmitter(0L);
        Thread worker = new Thread(() -> {
            try {
                Map<String, Object> result = playgroundService.runWorkflowStream(payload, userId, traceId, (eventName, data) -> {
                    try {
                        if ("trace".equals(eventName)) {
                            emitter.send(SseEmitter.event().name("trace").data(data));
                        } else if ("error".equals(eventName)) {
                            emitter.send(SseEmitter.event().name("error").data(data));
                        }
                    } catch (IOException ioException) {
                        throw new RuntimeException(ioException);
                    }
                });
                if (result == null) {
                    throw new IllegalStateException("Playground runtime returned empty result.");
                }
                emitter.send(SseEmitter.event().name("final").data(result));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(Map.of("message", resolveErrorMessage(e))));
                    emitter.complete();
                } catch (IOException ignored) {
                    emitter.completeWithError(e);
                }
            }
        }, "playground-run-stream");
        worker.setDaemon(true);
        worker.start();
        return emitter;
    }

    /**
     * Internal LLM call endpoint for the Python AI engine.
     * No API key required — covered by /api/playground/** permitAll() in SecurityConfig.
     * Delegates to the configured LLM provider via the gateway RouterService.
     */
    @PostMapping("/llm")
    public Map<String, Object> llm(@RequestBody Map<String, Object> payload) {
        String systemPrompt = String.valueOf(payload.getOrDefault("system_prompt", "You are a helpful assistant."));
        String userInput = String.valueOf(payload.getOrDefault("user_input", ""));
        String model = payload.get("model") instanceof String s && !s.isBlank() ? normalizeRuntimeModelName(s) : null;

        ChatCompletionRequest req = new ChatCompletionRequest();
        req.setStream(false);
        if (model != null) req.setModel(model);
        if (payload.get("temperature") instanceof Number temperature) {
            req.setTemperature(temperature.doubleValue());
        }
        if (payload.get("max_tokens") instanceof Number maxTokens) {
            req.setMaxTokens(Math.max(1, Math.min(16000, maxTokens.intValue())));
        }

        List<ChatCompletionRequest.Message> messages = new ArrayList<>();
        if (!systemPrompt.isBlank()) {
            ChatCompletionRequest.Message sys = new ChatCompletionRequest.Message();
            sys.setRole("system");
            sys.setContent(systemPrompt);
            messages.add(sys);
        }
        ChatCompletionRequest.Message user = new ChatCompletionRequest.Message();
        user.setRole("user");
        user.setContent(userInput);
        messages.add(user);
        req.setMessages(messages);

        Optional<ProviderAdapter> providerOpt = model != null
                ? routerService.selectProviderByModel(model, req)
                : routerService.selectProvider(req, RouterService.RoutingStrategy.LOWEST_COST);

        if (providerOpt.isEmpty()) {
            log.warn("Playground LLM: no available provider");
            return Map.of("text", "", "error", "No LLM provider available. Configure a provider in System Settings.");
        }

        try {
            ProviderAdapter provider = providerOpt.get();
            ChatCompletionResponse response;
            try {
                response = provider.chatCompletion(req).block();
            } catch (Exception firstError) {
                Optional<String> fallbackModel = findFallbackChatModel(provider, model);
                if (fallbackModel.isEmpty()) {
                    throw firstError;
                }
                log.warn("Playground LLM model '{}' failed: {}. Retrying with fallback model '{}'.",
                        model, resolveErrorMessage(firstError), fallbackModel.get());
                req.setModel(fallbackModel.get());
                response = provider.chatCompletion(req).block();
            }
            String text = (response != null && response.getChoices() != null && !response.getChoices().isEmpty())
                    ? response.getChoices().get(0).getMessage().getContent()
                    : "";
            return Map.of("text", text, "model", req.getModel() != null ? req.getModel() : "");
        } catch (Exception e) {
            String errorMessage = resolveErrorMessage(e);
            log.warn("Playground LLM call failed: {}", errorMessage);
            return Map.of("text", "", "error", errorMessage);
        }
    }

    @GetMapping("/conversations")
    public List<Map<String, Object>> conversations(@RequestParam(value = "workflow_id", required = false) String workflowId) {
        return playgroundService.listConversations(workflowId);
    }

    @PostMapping("/conversations")
    public Map<String, Object> createConversation(@RequestBody Map<String, Object> payload) {
        return playgroundService.createConversation(payload);
    }

    @GetMapping("/conversations/{conversationId}")
    public Map<String, Object> conversation(@PathVariable String conversationId) {
        return playgroundService.getConversation(conversationId);
    }

    @DeleteMapping("/conversations/{conversationId}")
    public Map<String, Object> deleteConversation(@PathVariable String conversationId) {
        return playgroundService.deleteConversation(conversationId);
    }

    private String resolveErrorMessage(Throwable error) {
        if (error == null) {
            return "Unknown playground error.";
        }
        if (error.getMessage() != null && !error.getMessage().isBlank()) {
            return error.getMessage();
        }
        Throwable cause = error.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return cause.getMessage();
        }
        return error.getClass().getSimpleName();
    }

    private Optional<String> findFallbackChatModel(ProviderAdapter provider, String failedModel) {
        if (provider == null) {
            return Optional.empty();
        }
        try {
            Map<String, Object> modelResponse = provider.getModels().block();
            Set<String> modelIds = new LinkedHashSet<>();
            collectModelIds(modelResponse, modelIds);
            String failed = failedModel == null ? "" : failedModel.trim();
            return modelIds.stream()
                    .map(String::trim)
                    .filter(model -> !model.isBlank())
                    .filter(model -> !model.equals(failed))
                    .filter(this::isLikelyChatModel)
                    .findFirst();
        } catch (Exception e) {
            log.warn("Playground LLM fallback model lookup failed: {}", resolveErrorMessage(e));
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private void collectModelIds(Object value, Set<String> collector) {
        if (value == null || collector.size() >= 64) {
            return;
        }
        if (value instanceof Map<?, ?> map) {
            Object id = map.get("id");
            if (id instanceof String s && !s.isBlank()) {
                collector.add(normalizeRuntimeModelName(s));
            }
            Object modelId = map.get("modelId");
            if (modelId instanceof String s && !s.isBlank()) {
                collector.add(normalizeRuntimeModelName(s));
            }
            Object modelName = map.get("modelName");
            if (modelName instanceof String s && !s.isBlank()) {
                collector.add(normalizeRuntimeModelName(s));
            }
            for (Object nested : map.values()) {
                collectModelIds(nested, collector);
            }
            return;
        }
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                collectModelIds(item, collector);
            }
        }
    }

    private boolean isLikelyChatModel(String model) {
        String lower = model == null ? "" : model.toLowerCase();
        if (lower.isBlank()) {
            return false;
        }
        return !(lower.contains("embedding")
                || lower.contains("embed")
                || lower.contains("rerank")
                || lower.contains("whisper")
                || lower.contains("tts")
                || lower.contains("speech")
                || lower.contains("stable-diffusion")
                || lower.contains("flux")
                || lower.contains("image")
                || lower.contains("video")
                || lower.contains("wan-"));
    }

    private String normalizeRuntimeModelName(String rawModel) {
        if (rawModel == null) {
            return null;
        }
        String model = rawModel.trim();
        if (model.isBlank()) {
            return null;
        }
        return model;
    }
}
