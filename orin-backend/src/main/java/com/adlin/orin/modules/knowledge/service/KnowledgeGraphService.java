package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.modules.knowledge.entity.GraphBuildState;
import com.adlin.orin.modules.knowledge.entity.GraphEntity;
import com.adlin.orin.modules.knowledge.entity.GraphRelation;
import com.adlin.orin.modules.knowledge.entity.KnowledgeGraph;
import com.adlin.orin.modules.knowledge.repository.GraphEntityRepository;
import com.adlin.orin.modules.knowledge.repository.GraphRelationRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeGraphRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeGraphService {

    private final KnowledgeGraphRepository knowledgeGraphRepository;
    private final GraphEntityRepository graphEntityRepository;
    private final GraphRelationRepository graphRelationRepository;

    /**
     * 获取所有图谱列表
     */
    public List<KnowledgeGraph> getAllGraphs() {
        return knowledgeGraphRepository.findAll();
    }

    /**
     * 获取图谱详情
     */
    public Optional<KnowledgeGraph> getGraphById(String graphId) {
        return knowledgeGraphRepository.findById(graphId);
    }

    /**
     * 创建图谱
     */
    @Transactional
    public KnowledgeGraph createGraph(KnowledgeGraph graph) {
        graph.setBuildStatus(GraphBuildState.PENDING);
        graph.setEntityCount(0);
        graph.setRelationCount(0);
        return knowledgeGraphRepository.save(graph);
    }

    /**
     * 更新图谱
     */
    @Transactional
    public KnowledgeGraph updateGraph(String graphId, KnowledgeGraph updates) {
        Optional<KnowledgeGraph> existing = knowledgeGraphRepository.findById(graphId);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("图谱不存在: " + graphId);
        }

        KnowledgeGraph graph = existing.get();
        if (updates.getName() != null) {
            graph.setName(updates.getName());
        }
        if (updates.getDescription() != null) {
            graph.setDescription(updates.getDescription());
        }
        return knowledgeGraphRepository.save(graph);
    }

    /**
     * 删除图谱
     */
    @Transactional
    public void deleteGraph(String graphId) {
        // 先删除所有实体和关系
        graphEntityRepository.deleteByGraphId(graphId);
        graphRelationRepository.deleteByGraphId(graphId);
        knowledgeGraphRepository.deleteById(graphId);
    }

    /**
     * 触发图谱构建
     */
    @Transactional
    public KnowledgeGraph triggerBuild(String graphId) {
        Optional<KnowledgeGraph> existing = knowledgeGraphRepository.findById(graphId);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("图谱不存在: " + graphId);
        }

        KnowledgeGraph graph = existing.get();
        graph.setBuildStatus(GraphBuildState.BUILDING);
        return knowledgeGraphRepository.save(graph);
    }

    /**
     * 获取图谱实体列表（分页）
     */
    public Page<GraphEntity> getGraphEntities(String graphId, Pageable pageable) {
        return graphEntityRepository.findByGraphId(graphId, pageable);
    }

    /**
     * 获取图谱关系列表（分页）
     */
    public Page<GraphRelation> getGraphRelations(String graphId, Pageable pageable) {
        return graphRelationRepository.findByGraphId(graphId, pageable);
    }

    /**
     * 搜索图谱实体
     */
    public List<GraphEntity> searchEntities(String graphId, String keyword) {
        return graphEntityRepository.findByGraphIdAndNameContainingIgnoreCase(graphId, keyword);
    }

    /**
     * 更新图谱统计信息
     */
    @Transactional
    public void updateGraphStats(String graphId) {
        Optional<KnowledgeGraph> existing = knowledgeGraphRepository.findById(graphId);
        if (existing.isEmpty()) {
            return;
        }

        KnowledgeGraph graph = existing.get();
        long entityCount = graphEntityRepository.countByGraphId(graphId);
        long relationCount = graphRelationRepository.countByGraphId(graphId);

        graph.setEntityCount((int) entityCount);
        graph.setRelationCount((int) relationCount);
        knowledgeGraphRepository.save(graph);
    }
}
