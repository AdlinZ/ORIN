package com.adlin.orin.modules.model.controller;

import com.adlin.orin.modules.model.service.SiliconFlowIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(name = "SiliconFlow File Proxy", description = "硅基流动文件上传代理")
public class SiliconFlowProxyController {

    private final SiliconFlowIntegrationService siliconFlowIntegrationService;

    // We hardcode API Key for now or get it from a default agent/config?
    // The frontend call to /files does NOT include agentId, so we don't know which
    // API Key to use if it's agent-specific.
    // However, `AgentList.vue` DOES NOT pass apiKey to `uploadFile`.
    // Wait, the frontend `uploadFile` just calls `request.post('/files', ...)`.
    // The previous frontend implementation of `chatAgent` relied on `AgentList.vue`
    // finding the agent.
    // BUT `uploadFile` is generic.
    // CHECK: `AgentList.vue` has `currentChatAgent`.
    // But `uploadFile` in `api/agent.js` does NOT take agentId or apiKey.
    // This is a problem. The backend needs an API Key to upload to SiliconFlow.
    // If SiliconFlow API Key is global, we can use a system default.
    // If it is per-agent, `uploadFile` MUST take an agentId or apiKey.
    //
    // Let's check `SiliconFlowIntegrationService` - it takes `apiKey` as param in
    // `uploadFile`.
    //
    // I need to update Frontend `uploadFile` to take `agentId` and pass it to
    // backend so backend can look up the API Key?
    // Or pass `apiKey` directly? Frontend shouldn't expose API Key if possible
    // (though `editForm` has it masked).
    //
    // Best approach: Pass `agentId` to `uploadFile`. Backend looks up profile.
    //
    // So I need to update Frontend again?
    // Let's assume for now I will fix the Backend to require `agentId` parameter in
    // `/files` or use a "Default" key if configured.
    //
    // Looking at `AgentManageServiceImpl`, it looks up `AgentAccessProfile` to get
    // API Key.
    //
    // I will add `agentId` param to `uploadFile` in this Controller.
    // And I will have to update Frontend `api/agent.js` and `AgentList.vue` to pass
    // it.

    // I will implement the controller to Expect `agentId` param.

    private final com.adlin.orin.modules.agent.service.AgentManageService agentManageService;

    @Operation(summary = "上传文件到硅基流动")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Object> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("purpose") String purpose,
            @RequestParam(value = "agentId", required = false) String agentId) {

        String apiKey = "";
        String endpointUrl = "https://api.siliconflow.cn/v1"; // Default

        if (agentId != null) {
            var profile = agentManageService.getAgentAccessProfile(agentId);
            apiKey = profile.getApiKey();
            if (profile.getEndpointUrl() != null && !profile.getEndpointUrl().isEmpty()) {
                endpointUrl = profile.getEndpointUrl();
            }
        } else {
            // Try to find a system default or error?
            // tailored for user request: The user is chatting with an agent.
            return ResponseEntity.badRequest().body(Map.of("message", "agentId is required to determine API Key"));
        }

        var result = siliconFlowIntegrationService.uploadFile(endpointUrl, apiKey, file);
        return result.map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(500).body(Map.of("message", "Upload failed")));
    }
}
