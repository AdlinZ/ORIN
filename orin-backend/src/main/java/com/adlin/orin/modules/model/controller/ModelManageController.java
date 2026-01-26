package com.adlin.orin.modules.model.controller;

import com.adlin.orin.modules.model.entity.ModelMetadata;
import com.adlin.orin.modules.model.service.ModelFetchService;
import com.adlin.orin.modules.model.service.ModelManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/models")
@RequiredArgsConstructor
@Tag(name = "Model Management", description = "LLM 模型资源管理")
@CrossOrigin(origins = "*")
public class ModelManageController {

    private final ModelManageService modelManageService;
    private final ModelFetchService modelFetchService;

    @Operation(summary = "获取所有模型列表")
    @GetMapping
    public List<ModelMetadata> listModels() {
        return modelManageService.getAllModels();
    }

    @Operation(summary = "新建/编辑模型")
    @PostMapping
    public ModelMetadata saveModel(@RequestBody ModelMetadata model) {
        return modelManageService.saveModel(model);
    }

    @Operation(summary = "删除模型")
    @DeleteMapping("/{id}")
    public void deleteModel(@PathVariable Long id) {
        modelManageService.deleteModel(id);
    }

    @Operation(summary = "切换模型启用状态")
    @PatchMapping("/{id}/toggle")
    public ModelMetadata toggleStatus(@PathVariable Long id) {
        return modelManageService.toggleStatus(id);
    }

    @Operation(summary = "从 API 获取可用模型列表 (OpenAI 兼容)")
    @GetMapping("/fetch")
    public List<java.util.Map<String, Object>> fetchModels(
            @RequestParam String baseUrl,
            @RequestParam String apiKey) {
        return modelFetchService.fetchModelsFromApi(baseUrl, apiKey);
    }
}
