package com.adlin.orin.modules.workflow.controller;

import com.adlin.orin.modules.workflow.dto.WorkflowRequest;
import com.adlin.orin.modules.workflow.dto.WorkflowResponse;
import com.adlin.orin.modules.workflow.dto.WorkflowStepRequest;
import com.adlin.orin.modules.workflow.entity.WorkflowInstanceEntity;
import com.adlin.orin.modules.workflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping
    @Operation(summary = "创建工作流")
    public ResponseEntity<WorkflowResponse> createWorkflow(@RequestBody WorkflowRequest request) {
        log.info("REST request to create workflow: {}", request.getWorkflowName());
        WorkflowResponse response = workflowService.createWorkflow(request);
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

    @GetMapping("/{id}/instances")
    @Operation(summary = "获取工作流执行实例列表")
    public ResponseEntity<List<WorkflowInstanceEntity>> getWorkflowInstances(@PathVariable Long id) {
        log.info("REST request to get workflow instances: {}", id);
        List<WorkflowInstanceEntity> instances = workflowService.getWorkflowInstances(id);
        return ResponseEntity.ok(instances);
    }
}
