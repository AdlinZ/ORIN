package com.adlin.orin.modules.multimodal.controller;

import com.adlin.orin.modules.multimodal.entity.MultimodalTask;
import com.adlin.orin.modules.multimodal.repository.MultimodalTaskRepository;
import com.adlin.orin.modules.multimodal.service.VisualAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/multimodal/tasks")
@RequiredArgsConstructor
@Tag(name = "Multimodal Task Management", description = "多模态任务后台执行与历史管理")
public class MultimodalTaskController {

    private final MultimodalTaskRepository taskRepository;
    private final VisualAnalysisService visualAnalysisService;

    @Operation(summary = "提交异步分析任务")
    @PostMapping("/submit")
    public MultimodalTask submitTask(@RequestBody Map<String, String> payload) {
        String imageUrl = payload.get("imageUrl");
        String modelName = payload.get("modelName");

        MultimodalTask task = new MultimodalTask();
        task.setTargetUrl(imageUrl);
        task.setModelName(modelName);
        task.setStatus("RUNNING");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());

        MultimodalTask savedTask = taskRepository.save(task);

        long startTime = System.currentTimeMillis();

        // 异步执行
        visualAnalysisService.analyzeImageAsync(imageUrl, modelName)
                .thenAccept(result -> {
                    long endTime = System.currentTimeMillis();
                    savedTask.setResult(result);
                    savedTask.setStatus("SUCCESS");
                    savedTask.setUpdatedAt(LocalDateTime.now());
                    savedTask.setExecutionTime(endTime - startTime);
                    taskRepository.save(savedTask);
                })
                .exceptionally(ex -> {
                    savedTask.setStatus("FAILED");
                    savedTask.setResult("Error: " + ex.getMessage());
                    savedTask.setUpdatedAt(LocalDateTime.now());
                    taskRepository.save(savedTask);
                    return null;
                });

        return savedTask;
    }

    @Operation(summary = "获取历史任务列表")
    @GetMapping("/list")
    public List<MultimodalTask> getTaskList() {
        return taskRepository.findAllByOrderByCreatedAtDesc();
    }

    @Operation(summary = "获取任务详情")
    @GetMapping("/{id}")
    public MultimodalTask getTask(@PathVariable Long id) {
        return taskRepository.findById(id).orElseThrow();
    }
}
