package com.adlin.orin.modules.knowledge.controller;

import com.adlin.orin.modules.knowledge.entity.GraphEntity;
import com.adlin.orin.modules.knowledge.entity.GraphRelation;
import com.adlin.orin.modules.knowledge.entity.KnowledgeGraph;
import com.adlin.orin.modules.knowledge.service.GraphExtractionService;
import com.adlin.orin.modules.knowledge.service.KnowledgeGraphService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/knowledge/graphs")
@RequiredArgsConstructor
@Tag(name = "Knowledge Graph", description = "知识图谱管理")
public class KnowledgeGraphController {

    private final KnowledgeGraphService knowledgeGraphService;
    private final GraphExtractionService graphExtractionService;

    @Operation(summary = "获取图谱列表")
    @GetMapping
    public ResponseEntity<List<KnowledgeGraph>> getGraphList() {
        return ResponseEntity.ok(knowledgeGraphService.getAllGraphs());
    }

    @Operation(summary = "获取图谱详情")
    @GetMapping("/{graphId}")
    public ResponseEntity<KnowledgeGraph> getGraph(@PathVariable String graphId) {
        return knowledgeGraphService.getGraphById(graphId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "创建图谱")
    @PostMapping
    public ResponseEntity<KnowledgeGraph> createGraph(@RequestBody KnowledgeGraph graph) {
        KnowledgeGraph created = knowledgeGraphService.createGraph(graph);
        return ResponseEntity.ok(created);
    }

    @Operation(summary = "更新图谱")
    @PutMapping("/{graphId}")
    public ResponseEntity<KnowledgeGraph> updateGraph(
            @PathVariable String graphId,
            @RequestBody KnowledgeGraph updates) {
        try {
            KnowledgeGraph updated = knowledgeGraphService.updateGraph(graphId, updates);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "删除图谱")
    @DeleteMapping("/{graphId}")
    public ResponseEntity<Void> deleteGraph(@PathVariable String graphId) {
        knowledgeGraphService.deleteGraph(graphId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "触发图谱构建")
    @PostMapping("/{graphId}/build")
    public ResponseEntity<Map<String, Object>> buildGraph(@PathVariable String graphId) {
        try {
            KnowledgeGraph graph = knowledgeGraphService.triggerBuild(graphId);
            graphExtractionService.buildGraphAsync(graphId);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "图谱构建任务已启动");
            result.put("graph", graph);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "获取图谱实体列表")
    @GetMapping("/{graphId}/entities")
    public ResponseEntity<Page<GraphEntity>> getGraphEntities(
            @PathVariable String graphId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<GraphEntity> entities = knowledgeGraphService.getGraphEntities(graphId, PageRequest.of(page, size));
        return ResponseEntity.ok(entities);
    }

    @Operation(summary = "获取图谱关系列表")
    @GetMapping("/{graphId}/relations")
    public ResponseEntity<Page<GraphRelation>> getGraphRelations(
            @PathVariable String graphId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<GraphRelation> relations = knowledgeGraphService.getGraphRelations(graphId, PageRequest.of(page, size));
        return ResponseEntity.ok(relations);
    }

    @Operation(summary = "搜索图谱实体")
    @GetMapping("/{graphId}/entities/search")
    public ResponseEntity<List<GraphEntity>> searchGraphEntities(
            @PathVariable String graphId,
            @RequestParam("q") String keyword) {
        List<GraphEntity> entities = knowledgeGraphService.searchEntities(graphId, keyword);
        return ResponseEntity.ok(entities);
    }

    @Operation(summary = "获取图谱可视化数据")
    @GetMapping("/{graphId}/visualization")
    public ResponseEntity<Map<String, Object>> getVisualizationData(
            @PathVariable String graphId,
            @RequestParam(value = "documentId", required = false) String documentId) {
        GraphExtractionService.GraphVisualizationData data =
                graphExtractionService.getVisualizationData(graphId, documentId);
        Map<String, Object> result = new HashMap<>();
        result.put("nodes", data.getNodes());
        result.put("edges", data.getEdges());
        result.put("categories", data.getCategories());
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "获取实体详情")
    @GetMapping("/{graphId}/entities/{entityId}/details")
    public ResponseEntity<Map<String, Object>> getEntityDetails(
            @PathVariable String graphId,
            @PathVariable String entityId) {
        return graphExtractionService.getEntityDetails(graphId, entityId)
                .map(entity -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("entity", entity);
                    result.put("relations", graphExtractionService.getEntityRelations(graphId, entityId));
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
