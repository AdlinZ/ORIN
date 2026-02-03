package com.adlin.orin.modules.agent.controller;

import com.adlin.orin.modules.agent.dto.AgentOnboardRequest;
import com.adlin.orin.modules.agent.entity.AgentAccessProfile;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.service.AgentManageService;
import com.adlin.orin.modules.agent.service.SiliconFlowAgentManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agents")
@Tag(name = "Phase 1: Agent Onboarding", description = "智能体接入与管理")
@CrossOrigin(origins = "*")
public class AgentManageController {

    private final AgentManageService agentManageService;
    private final SiliconFlowAgentManageService siliconFlowAgentManageService;
    private final com.adlin.orin.modules.agent.service.ZhipuAgentManageService zhipuAgentManageService;
    private final com.adlin.orin.modules.agent.service.DeepSeekAgentManageService deepSeekAgentManageService;
    private final com.adlin.orin.modules.agent.service.AgentVersionService agentVersionService;

    @Autowired
    public AgentManageController(AgentManageService agentManageService,
            SiliconFlowAgentManageService siliconFlowAgentManageService,
            com.adlin.orin.modules.agent.service.ZhipuAgentManageService zhipuAgentManageService,
            com.adlin.orin.modules.agent.service.DeepSeekAgentManageService deepSeekAgentManageService,
            com.adlin.orin.modules.agent.service.AgentVersionService agentVersionService) {
        this.agentManageService = agentManageService;
        this.siliconFlowAgentManageService = siliconFlowAgentManageService;
        this.zhipuAgentManageService = zhipuAgentManageService;
        this.deepSeekAgentManageService = deepSeekAgentManageService;
        this.agentVersionService = agentVersionService;
    }

    @Operation(summary = "接入新智能体(Dify)")
    @PostMapping("/onboard")
    public AgentMetadata onboardAgent(@RequestBody AgentOnboardRequest request) {
        return agentManageService.onboardAgent(request.getEndpointUrl(), request.getApiKey(),
                request.getDatasetApiKey());
    }

    @Operation(summary = "接入硅基流动智能体")
    @PostMapping("/onboard-silicon-flow")
    public AgentMetadata onboardSiliconFlowAgent(
            @RequestParam String endpointUrl,
            @RequestParam String apiKey,
            @RequestParam String model,
            @RequestParam(required = false) String name) {
        return siliconFlowAgentManageService.onboardAgent(endpointUrl, apiKey, model, name);
    }

    @Operation(summary = "接入智谱AI智能体")
    @PostMapping("/onboard-zhipu")
    public AgentMetadata onboardZhipuAgent(
            @RequestParam String endpointUrl,
            @RequestParam String apiKey,
            @RequestParam String model,
            @RequestParam(required = false) String name,
            @RequestParam(required = false, defaultValue = "1.0") Double temperature) {
        return zhipuAgentManageService.onboardAgent(endpointUrl, apiKey, model, name, temperature);
    }

    @Operation(summary = "接入DeepSeek智能体")
    @PostMapping("/onboard-deepseek")
    public AgentMetadata onboardDeepSeekAgent(
            @RequestParam String endpointUrl,
            @RequestParam String apiKey,
            @RequestParam String model,
            @RequestParam(required = false) String name,
            @RequestParam(required = false, defaultValue = "1.0") Double temperature) {
        return deepSeekAgentManageService.onboardAgent(endpointUrl, apiKey, model, name, temperature);
    }

    @Operation(summary = "更新智能体配置")
    @PutMapping("/{agentId}")
    public void updateAgent(
            @PathVariable String agentId,
            @RequestBody AgentOnboardRequest request) {
        agentManageService.updateAgent(agentId, request);
    }

    @Operation(summary = "与智能体对话 (Multipart)")
    @PostMapping(value = "/{agentId}/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object chat(
            @PathVariable String agentId,
            @RequestPart("message") String message,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return agentManageService.chat(agentId, message, file)
                .orElseThrow(() -> new RuntimeException("Chat failed"));
    }

    @Operation(summary = "与智能体对话 (JSON)")
    @PostMapping(value = "/{agentId}/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object chatJson(
            @PathVariable String agentId,
            @RequestBody ChatRequest request) {
        return agentManageService
                .chat(agentId, request.getMessage(), request.getFileId(), request.getOverrideSystemPrompt(),
                        request.getConversationId(), request.getEnableThinking(), request.getThinkingBudget())
                .orElseThrow(() -> new RuntimeException("Chat failed"));
    }

    @lombok.Data
    public static class ChatRequest {
        private String message;
        @com.fasterxml.jackson.annotation.JsonProperty("file_id")
        private String fileId;
        @com.fasterxml.jackson.annotation.JsonProperty("system_prompt")
        private String overrideSystemPrompt;
        @com.fasterxml.jackson.annotation.JsonProperty("conversation_id")
        private String conversationId;
        @com.fasterxml.jackson.annotation.JsonProperty("enable_thinking")
        private Boolean enableThinking;
        @com.fasterxml.jackson.annotation.JsonProperty("thinking_budget")
        private Integer thinkingBudget;
    }

    @Operation(summary = "获取所有已接入的智能体档案")
    @GetMapping
    public List<AgentMetadata> listAgents() {
        return agentManageService.getAllAgents();
    }

    @Operation(summary = "获取智能体元数据详情")
    @GetMapping("/{agentId}/metadata")
    public AgentMetadata getAgentMetadata(@PathVariable String agentId) {
        return agentManageService.getAgentMetadata(agentId);
    }

    @Operation(summary = "获取智能体接入配置详情")
    @GetMapping("/{agentId}/access-profile")
    public AgentAccessProfile getAgentAccessProfile(@PathVariable String agentId) {
        return agentManageService.getAgentAccessProfile(agentId);
    }

    @Operation(summary = "批量导出智能体配置")
    @PostMapping("/batch/export")
    public org.springframework.http.ResponseEntity<byte[]> batchExport(
            @RequestBody(required = false) List<String> agentIds) {
        byte[] content = agentManageService.batchExportAgents(agentIds);
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "agents_export.json");
        return new org.springframework.http.ResponseEntity<>(content, headers, org.springframework.http.HttpStatus.OK);
    }

    @Operation(summary = "批量导入智能体配置")
    @PostMapping(value = "/batch/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void batchImport(@RequestPart("file") MultipartFile file) {
        agentManageService.batchImportAgents(file);
    }

    @Operation(summary = "删除智能体")
    @DeleteMapping("/{agentId}")
    public void deleteAgent(@PathVariable String agentId) {
        agentManageService.deleteAgent(agentId);
    }

    @Operation(summary = "刷新所有智能体元数据")
    @PostMapping("/refresh")
    public void refreshAllAgents() {
        agentManageService.refreshAllAgentsMetadata();
    }

    @Operation(summary = "查询异步任务状态")
    @GetMapping("/{agentId}/jobs/{jobId}")
    public Object getJobStatus(
            @PathVariable String agentId,
            @PathVariable String jobId) {
        return agentManageService.getJobStatus(agentId, jobId);
    }

    // ==================== 版本管理 API ====================

    @Operation(summary = "获取智能体版本列表")
    @GetMapping("/{agentId}/versions")
    public List<com.adlin.orin.modules.agent.entity.AgentVersion> getVersions(@PathVariable String agentId) {
        return agentVersionService.getVersions(agentId);
    }

    @Operation(summary = "创建新版本")
    @PostMapping("/{agentId}/versions")
    public com.adlin.orin.modules.agent.entity.AgentVersion createVersion(
            @PathVariable String agentId,
            @RequestBody java.util.Map<String, String> body) {
        String description = body.getOrDefault("description", "Manual version creation");
        String createdBy = body.getOrDefault("createdBy", "system");
        return agentVersionService.createVersion(agentId, description, createdBy);
    }

    @Operation(summary = "获取指定版本详情")
    @GetMapping("/{agentId}/versions/{versionNumber}")
    public com.adlin.orin.modules.agent.entity.AgentVersion getVersion(
            @PathVariable String agentId,
            @PathVariable Integer versionNumber) {
        return agentVersionService.getVersion(agentId, versionNumber);
    }

    @Operation(summary = "回滚到指定版本")
    @PostMapping("/{agentId}/versions/{versionId}/rollback")
    public AgentMetadata rollbackToVersion(
            @PathVariable String agentId,
            @PathVariable String versionId) {
        return agentVersionService.rollbackToVersion(agentId, versionId);
    }

    @Operation(summary = "对比两个版本")
    @GetMapping("/{agentId}/versions/compare")
    public java.util.Map<String, Object> compareVersions(
            @PathVariable String agentId,
            @RequestParam Integer version1,
            @RequestParam Integer version2) {
        return agentVersionService.compareVersions(agentId, version1, version2);
    }
}
