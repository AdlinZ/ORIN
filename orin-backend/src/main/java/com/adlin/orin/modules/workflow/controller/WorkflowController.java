package com.adlin.orin.modules.workflow.controller;

import com.adlin.orin.modules.workflow.dto.WorkflowRequest;
import com.adlin.orin.modules.workflow.dto.WorkflowResponse;
import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.modules.workflow.dto.WorkflowStepRequest;
import com.adlin.orin.modules.workflow.entity.WorkflowInstanceEntity;
import com.adlin.orin.modules.workflow.service.WorkflowService;
import com.adlin.orin.modules.workflow.service.WorkflowGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import java.util.List;
import java.util.Map;

/**
 * 工作流管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
@Tag(name = "Workflow Management", description = "工作流管理 API")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowGenerationService workflowGenerationService;

    @PostMapping("/generate")
    @Operation(summary = "AI 辅助生成工作流图结构")
    public ResponseEntity<Map<String, Object>> generateWorkflow(@RequestBody Map<String, String> request) {
        String prompt = request.get("prompt");
        if (prompt == null || prompt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Prompt is required"));
        }
        log.info("REST request to generate AI workflow: {}", prompt);
        try {
            Map<String, Object> graph = workflowGenerationService.generateWorkflow(prompt);
            return ResponseEntity.ok(graph);
        } catch (BusinessException e) {
            throw e; // Let GlobalExceptionHandler handle BusinessException with proper structure
        } catch (Exception e) {
            log.error("AI generation failed unexpectedly", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "AI 生成工作流失败"));
        }

    }

    @PostMapping
    @Operation(summary = "创建工作流")
    public ResponseEntity<WorkflowResponse> createWorkflow(@RequestBody WorkflowRequest request) {
        log.info("REST request to create workflow: {}", request.getWorkflowName());
        WorkflowResponse response = workflowService.createWorkflow(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/import/dify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "导入 Dify 工作流 DSL")
    public ResponseEntity<WorkflowResponse> importDifyWorkflow(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description) throws IOException {
        log.info("REST request to import Dify workflow: {}", name);
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        WorkflowResponse response = workflowService.importDifyWorkflow(name, description, content);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/steps")
    @Operation(summary = "添加工作流步骤")
    public ResponseEntity<Void> addStep(
            @PathVariable Long id,
            @RequestBody WorkflowStepRequest request) {
        log.info("REST request to add step to workflow: {}", id);
        workflowService.addStep(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/{id}/execute")
    @Operation(summary = "执行工作流")
    public ResponseEntity<Map<String, Object>> executeWorkflow(
            @PathVariable Long id,
            @RequestBody Map<String, Object> inputs,
            @RequestParam(required = false, defaultValue = "system") String triggeredBy) {
        log.info("REST request to execute workflow: {}", id);
        Long instanceId = workflowService.triggerWorkflow(id, inputs, triggeredBy);
        return ResponseEntity.ok(Map.of("instanceId", instanceId));
    }

    @GetMapping
    @Operation(summary = "获取所有工作流")
    public ResponseEntity<List<WorkflowResponse>> getAllWorkflows() {
        log.info("REST request to get all workflows");
        List<WorkflowResponse> workflows = workflowService.getAllWorkflows();
        return ResponseEntity.ok(workflows);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取工作流详情")
    public ResponseEntity<WorkflowResponse> getWorkflow(@PathVariable Long id) {
        log.info("REST request to get workflow: {}", id);
        WorkflowResponse response = workflowService.getWorkflowById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/instances/{instanceId}")
    @Operation(summary = "获取工作流执行实例详情")
    public ResponseEntity<WorkflowInstanceEntity> getInstance(@PathVariable Long instanceId) {
        log.info("REST request to get workflow instance: {}", instanceId);
        WorkflowInstanceEntity instance = workflowService.getInstance(instanceId);
        return ResponseEntity.ok(instance);
    }

    @GetMapping("/{id}/instances")
    @Operation(summary = "获取工作流执行实例列表")
    public ResponseEntity<List<WorkflowInstanceEntity>> getWorkflowInstances(@PathVariable Long id) {
        log.info("REST request to get workflow instances: {}", id);
        List<WorkflowInstanceEntity> instances = workflowService.getWorkflowInstances(id);
        return ResponseEntity.ok(instances);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除工作流")
    public ResponseEntity<Void> deleteWorkflow(@PathVariable Long id) {
        log.info("REST request to delete workflow: {}", id);
        workflowService.deleteWorkflow(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/access")
    @Operation(summary = "获取工作流访问信息 (API/WebApp)")
    public ResponseEntity<com.adlin.orin.modules.workflow.dto.WorkflowAccessResponse> getWorkflowAccess(
            @PathVariable Long id) {
        log.info("REST request to get workflow access info: {}", id);
        return ResponseEntity.ok(workflowService.getWorkflowAccessInfo(id));
    }
}
