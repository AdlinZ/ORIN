package com.adlin.orin.modules.workflow.controller;

import com.adlin.orin.modules.workflow.dto.WorkflowResponse;
import com.adlin.orin.modules.workflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Dify 工作流同步控制器
 * 提供从 Dify 同步工作流的功能
 */
@Slf4j
@RestController
@RequestMapping("/api/workflows/sync")
@RequiredArgsConstructor
@Tag(name = "Dify Workflow Sync", description = "Dify 工作流同步管理")
public class DifyWorkflowSyncController {

    private final WorkflowService workflowService;
    private final RestTemplate difyRestTemplate;

    @Value("${dify.default.endpoint:http://localhost:3000/v1}")
    private String defaultEndpoint;

    @Value("${dify.default.api-key:}")
    private String defaultApiKey;

    @Operation(summary = "获取 Dify 工作流列表")
    @GetMapping("/dify/workflows")
    public ResponseEntity<Map<String, Object>> listDifyWorkflows(
            @RequestParam(required = false) String endpoint,
            @RequestParam(required = false) String apiKey) {

        String finalEndpoint = endpoint != null ? endpoint : defaultEndpoint;
        String finalApiKey = apiKey != null ? apiKey : defaultApiKey;

        if (finalApiKey == null || finalApiKey.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "API Key is required"));
        }

        try {
            String url = buildUrl(finalEndpoint, "/console/api/workspaces");

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(finalApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // Get workflows for the workspace
                Map<String, Object> workspaceData = response.getBody();
                List<Map<String, Object>> workspaces = (List<Map<String, Object>>) workspaceData.get("data");

                if (workspaces != null && !workspaces.isEmpty()) {
                    String workspaceId = (String) workspaces.get(0).get("id");
                    return listWorkflowsInWorkspace(finalEndpoint, finalApiKey, workspaceId);
                }
            }

            return ResponseEntity.ok(Map.of("data", new ArrayList<>()));
        } catch (Exception e) {
            log.error("Failed to list Dify workflows: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to connect to Dify: " + e.getMessage()));
        }
    }

    @Operation(summary = "从 Dify 导入工作流")
    @PostMapping("/dify/import")
    public ResponseEntity<Map<String, Object>> importFromDify(
            @RequestBody Map<String, String> request) {

        String endpoint = request.get("endpoint");
        String apiKey = request.get("apiKey");
        String workflowId = request.get("workflowId");
        String name = request.get("name");

        String finalEndpoint = endpoint != null ? endpoint : defaultEndpoint;
        String finalApiKey = apiKey != null ? apiKey : defaultApiKey;

        if (finalApiKey == null || finalApiKey.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "API Key is required"));
        }

        if (workflowId == null || workflowId.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Workflow ID is required"));
        }

        try {
            // Get workflow info and DSL
            String url = buildUrl(finalEndpoint, "/console/api/workflows/" + workflowId + "/export");

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(finalApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<String> response = difyRestTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new org.springframework.core.ParameterizedTypeReference<String>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String yamlContent = response.getBody();

                // Import as workflow
                String workflowName = name != null && !name.isEmpty() ? name : "Imported from Dify";
                WorkflowResponse workflow = workflowService.importDifyWorkflow(
                        workflowName,
                        "Imported from Dify",
                        yamlContent);

                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("workflow", workflow);
                result.put("message", "Workflow imported successfully");

                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Failed to export workflow from Dify"));
            }
        } catch (Exception e) {
            log.error("Failed to import Dify workflow: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to import workflow: " + e.getMessage()));
        }
    }

    @Operation(summary = "测试 Dify 连接")
    @PostMapping("/dify/test")
    public ResponseEntity<Map<String, Object>> testConnection(
            @RequestBody Map<String, String> request) {

        String endpoint = request.get("endpoint");
        String apiKey = request.get("apiKey");

        String finalEndpoint = endpoint != null ? endpoint : defaultEndpoint;
        String finalApiKey = apiKey != null ? apiKey : defaultApiKey;

        if (finalApiKey == null || finalApiKey.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "API Key is required"));
        }

        try {
            String url = buildUrl(finalEndpoint, "/console/api/workspaces");

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(finalApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

            boolean success = response.getStatusCode().is2xxSuccessful();

            return ResponseEntity.ok(Map.of(
                    "success", success,
                    "message", success ? "Connection successful" : "Connection failed"));
        } catch (Exception e) {
            log.error("Dify connection test failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Connection failed: " + e.getMessage()));
        }
    }

    private ResponseEntity<Map<String, Object>> listWorkflowsInWorkspace(String endpoint, String apiKey, String workspaceId) {
        try {
            String url = buildUrl(endpoint, "/console/api/workflows?workspace_id=" + workspaceId);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map<String, Object>> response = difyRestTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> result = response.getBody();
                List<Map<String, Object>> workflows = (List<Map<String, Object>>) result.get("data");

                List<Map<String, Object>> workflowList = new ArrayList<>();
                if (workflows != null) {
                    for (Map<String, Object> wf : workflows) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("id", wf.get("id"));
                        item.put("name", wf.get("name"));
                        item.put("mode", wf.get("mode"));
                        item.put("status", wf.get("status"));
                        item.put("created_at", wf.get("created_at"));
                        item.put("updated_at", wf.get("updated_at"));
                        workflowList.add(item);
                    }
                }

                return ResponseEntity.ok(Map.of("data", workflowList));
            }

            return ResponseEntity.ok(Map.of("data", new ArrayList<>()));
        } catch (Exception e) {
            log.error("Failed to list workflows in workspace: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to list workflows: " + e.getMessage()));
        }
    }

    private String buildUrl(String endpointUrl, String path) {
        String base = endpointUrl != null ? endpointUrl : "http://localhost:3000/v1";

        if (!base.startsWith("http")) {
            base = "http://" + base;
        }

        // Remove trailing /v1 or /v1/ to avoid duplication
        if (base.endsWith("/v1") || base.endsWith("/v1/")) {
            base = base.replaceAll("/v1/?$", "");
        }

        if (!base.endsWith("/")) {
            base += "/";
        }

        // Ensure path starts with console/api
        if (!path.startsWith("console/api/")) {
            path = "console/api/" + path.replaceFirst("^/?", "");
        }

        return base + path;
    }
}
