package com.adlin.orin.modules.model.controller;

import com.adlin.orin.modules.model.entity.ModelConfig;
import com.adlin.orin.modules.model.service.ModelConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Model Configuration", description = "模型系统配置管理")
@RestController
@RequestMapping("/api/v1/model-config")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class ModelConfigController {

    private final ModelConfigService modelConfigService;

    @Operation(summary = "获取模型系统配置")
    @GetMapping
    public ModelConfig getConfig() {
        return modelConfigService.getConfig();
    }

    @Operation(summary = "更新模型系统配置")
    @PutMapping
    public ModelConfig updateConfig(@RequestBody ModelConfig config) {
        return modelConfigService.updateConfig(config);
    }

    @Operation(summary = "测试Dify连接")
    @PostMapping("/test-dify-connection")
    public boolean testDifyConnection(@RequestParam String endpoint, @RequestParam String apiKey) {
        return modelConfigService.testDifyConnection(endpoint, apiKey);
    }

    @Operation(summary = "测试硅基流动连接")
    @PostMapping("/test-silicon-flow-connection")
    public Boolean testSiliconFlowConnection(
            @RequestParam String endpoint,
            @RequestParam String apiKey,
            @RequestParam(required = false) String model) {
        try {
            return modelConfigService.testSiliconFlowConnection(endpoint, apiKey,
                    model != null ? model : "Qwen/Qwen2-7B-Instruct");
        } catch (Exception e) {
            log.error("测试硅基流动连接时发生错误: ", e);
            return false;
        }
    }

    @Operation(summary = "测试智谱AI连接")
    @PostMapping("/test-zhipu-connection")
    public Boolean testZhipuConnection(
            @RequestParam String endpoint,
            @RequestParam String apiKey,
            @RequestParam(required = false) String model) {
        try {
            return modelConfigService.testZhipuConnection(endpoint, apiKey, model != null ? model : "glm-4");
        } catch (Exception e) {
            log.error("测试智谱AI连接时发生错误: ", e);
            return false;
        }
    }

    @Operation(summary = "测试DeepSeek连接")
    @PostMapping("/test-deepseek-connection")
    public Boolean testDeepSeekConnection(
            @RequestParam String endpoint,
            @RequestParam String apiKey,
            @RequestParam(required = false) String model) {
        try {
            return modelConfigService.testDeepSeekConnection(endpoint, apiKey, model != null ? model : "deepseek-chat");
        } catch (Exception e) {
            log.error("测试DeepSeek连接时发生错误: ", e);
            return false;
        }
    }
}