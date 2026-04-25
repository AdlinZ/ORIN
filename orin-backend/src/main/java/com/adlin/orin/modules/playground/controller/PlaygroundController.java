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
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
            long startedAt = System.currentTimeMillis();
            AtomicReference<Map<String, Object>> resultRef = new AtomicReference<>();
            AtomicReference<Throwable> errorRef = new AtomicReference<>();
            FutureTask<Void> runTask = new FutureTask<>(() -> {
                Map<String, Object> result = playgroundService.runWorkflow(payload, userId, traceId);
                resultRef.set(result);
                return null;
            });
            Thread runtimeThread = new Thread(runTask, "playground-runtime");
            runtimeThread.setDaemon(true);
            runtimeThread.start();

            try {
                while (!runTask.isDone()) {
                    long elapsed = (System.currentTimeMillis() - startedAt) / 1000;
                    emitter.send(SseEmitter.event().name("trace").data(Map.of(
                            "type", "node_progress",
                            "title", "Runtime Executing",
                            "detail", "Workflow is still running (" + elapsed + "s).",
                            "status", "RUNNING",
                            "at", OffsetDateTime.now().toString(),
                            "payload", Map.of(
                                    "node_id", "runtime_wait",
                                    "status", "RUNNING",
                                    "execution_path", "langgraph_mq"
                            )
                    )));
                    Thread.sleep(1500L);
                }

                try {
                    runTask.get();
                } catch (Exception e) {
                    errorRef.set(e.getCause() != null ? e.getCause() : e);
                }

                if (errorRef.get() != null) {
                    throw new RuntimeException(resolveErrorMessage(errorRef.get()), errorRef.get());
                }

                Map<String, Object> result = resultRef.get();
                if (result == null) {
                    throw new IllegalStateException("Playground runtime returned empty result.");
                }
                Object traceObj = result.get("trace");
                if (traceObj instanceof List<?> trace) {
                    for (Object event : trace) {
                        emitter.send(SseEmitter.event().name("trace").data(event));
                    }
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
        String model = payload.get("model") instanceof String s && !s.isBlank() ? s : null;

        ChatCompletionRequest req = new ChatCompletionRequest();
        req.setStream(false);
        if (model != null) req.setModel(model);

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
            ChatCompletionResponse response = providerOpt.get().chatCompletion(req).block();
            String text = (response != null && response.getChoices() != null && !response.getChoices().isEmpty())
                    ? response.getChoices().get(0).getMessage().getContent()
                    : "";
            return Map.of("text", text);
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
}
