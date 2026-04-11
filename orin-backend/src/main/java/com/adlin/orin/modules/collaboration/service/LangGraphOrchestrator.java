package com.adlin.orin.modules.collaboration.service;

import com.adlin.orin.modules.collaboration.dto.CollaborationPackage;
import com.adlin.orin.modules.collaboration.entity.CollaborationPackageEntity;
import com.adlin.orin.modules.collaboration.repository.CollaborationPackageRepository;
import com.adlin.orin.modules.collaboration.event.CollaborationEventBus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * LangGraph 协作编排服务
 * 包装 Python LangGraph 执行器
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LangGraphOrchestrator {

    private final CollaborationPackageRepository packageRepository;
    private final CollaborationEventBus eventBus;
    private final ObjectMapper objectMapper;

    @Value("${orin.ai-engine.url:http://localhost:8000}")
    private String aiEngineUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 通过 LangGraph 执行协作
     */
    public CollaborationPackage executeWithLangGraph(String packageId, String intent, String mode) {
        log.info("[LangGraph] 开始执行: packageId={}, mode={}", packageId, mode);

        try {
            // 调用 Python LangGraph 服务
            String url = aiEngineUrl + "/api/collaboration/run";
            
            Map<String, Object> request = new HashMap<>();
            request.put("package_id", packageId);
            request.put("intent", intent);
            request.put("collaboration_mode", mode);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> result = response.getBody();
                
                // 更新 Package 状态
                CollaborationPackage pkg = packageRepository.findByPackageId(packageId);
                if (pkg != null) {
                    pkg.setStatus((String) result.getOrDefault("status", "COMPLETED"));
                    pkg.setResult((String) result.get("final_result"));
                    pkg.setUpdatedAt(LocalDateTime.now());
                    packageRepository.save(pkg);
                    
                    // 发送完成事件
                    eventBus.publish(CollaborationEventBus.EventType.TASK_COMPLETED, packageId, pkg);
                    
                    log.info("[LangGraph] 执行完成: packageId={}", packageId);
                    return pkg;
                }
            }

        } catch (Exception e) {
            log.error("[LangGraph] 执行失败: packageId={}, error={}", packageId, e.getMessage());
            
            // 更新失败状态
            CollaborationPackage pkg = packageRepository.findByPackageId(packageId);
            if (pkg != null) {
                pkg.setStatus("FAILED");
                pkg.setErrorMessage(e.getMessage());
                pkg.setUpdatedAt(LocalDateTime.now());
                packageRepository.save(pkg);
            }
        }

        return null;
    }

    /**
     * 获取协作状态
     */
    public Map<String, Object> getCollaborationStatus(String packageId) {
        try {
            String url = aiEngineUrl + "/api/collaboration/status/" + packageId;
            
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("[LangGraph] 获取状态失败: packageId={}, error={}", packageId, e.getMessage());
        }
        
        return Collections.emptyMap();
    }

    /**
     * 暂停协作
     */
    public boolean pauseCollaboration(String packageId) {
        try {
            String url = aiEngineUrl + "/api/collaboration/pause";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, String> request = Collections.singletonMap("package_id", packageId);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);
            
            restTemplate.postForEntity(url, entity, Map.class);
            
            log.info("[LangGraph] 暂停成功: packageId={}", packageId);
            return true;
        } catch (Exception e) {
            log.error("[LangGraph] 暂停失败: packageId={}, error={}", packageId, e.getMessage());
            return false;
        }
    }

    /**
     * 恢复协作
     */
    public boolean resumeCollaboration(String packageId) {
        try {
            String url = aiEngineUrl + "/api/collaboration/resume";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, String> request = Collections.singletonMap("package_id", packageId);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);
            
            restTemplate.postForEntity(url, entity, Map.class);
            
            log.info("[LangGraph] 恢复成功: packageId={}", packageId);
            return true;
        } catch (Exception e) {
            log.error("[LangGraph] 恢复失败: packageId={}, error={}", packageId, e.getMessage());
            return false;
        }
    }

    /**
     * 回滚到检查点
     */
    public boolean rollbackToCheckpoint(String packageId, String checkpointId) {
        try {
            String url = aiEngineUrl + "/api/collaboration/rollback";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, String> request = new HashMap<>();
            request.put("package_id", packageId);
            request.put("checkpoint_id", checkpointId);
            
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);
            
            restTemplate.postForEntity(url, entity, Map.class);
            
            log.info("[LangGraph] 回滚成功: packageId={}, checkpoint={}", packageId, checkpointId);
            return true;
        } catch (Exception e) {
            log.error("[LangGraph] 回滚失败: packageId={}, error={}", packageId, e.getMessage());
            return false;
        }
    }
}
