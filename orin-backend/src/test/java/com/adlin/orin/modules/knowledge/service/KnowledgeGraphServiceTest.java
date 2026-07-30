package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.modules.knowledge.entity.GraphBuildState;
import com.adlin.orin.modules.knowledge.entity.GraphEntity;
import com.adlin.orin.modules.knowledge.entity.GraphRelation;
import com.adlin.orin.modules.knowledge.entity.KnowledgeGraph;
import com.adlin.orin.modules.knowledge.repository.GraphEntityRepository;
import com.adlin.orin.modules.knowledge.repository.GraphRelationRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeGraphRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * F1.2 知识图谱模块 service 级核心测试
 *
 * 测试目标：验证 KnowledgeGraphService 的核心 CRUD 路径
 * - 图谱创建、查询、更新、删除
 * - 触发构建（伪完成状态）
 * - 实体/关系查询
 * - 统计更新
 *
 * 运行方式：mvn test -Dtest=KnowledgeGraphServiceTest
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeGraphServiceTest {

    @Mock
    private KnowledgeGraphRepository knowledgeGraphRepository;

    @Mock
    private GraphEntityRepository graphEntityRepository;

    @Mock
    private GraphRelationRepository graphRelationRepository;

    private KnowledgeGraphService knowledgeGraphService;

    @BeforeEach
    void setUp() {
        knowledgeGraphService = new KnowledgeGraphService(
                knowledgeGraphRepository,
                graphEntityRepository,
                graphRelationRepository
        );
    }

    // ==================== 图谱 CRUD ====================

    @Test
    @DisplayName("F1.2 - 创建图谱：成功创建并设置默认状态")
    void testCreateGraph_Success() {
        // Given
        KnowledgeGraph graph = KnowledgeGraph.builder()
                .name("测试图谱")
                .description("这是一个测试图谱")
                .build();

        when(knowledgeGraphRepository.save(any(KnowledgeGraph.class)))
                .thenAnswer(inv -> {
                    KnowledgeGraph g = inv.getArgument(0);
                    g.setId("graph-001");
                    return g;
                });

        // When
        KnowledgeGraph result = knowledgeGraphService.createGraph(graph);

        // Then
        assertNotNull(result);
        assertEquals("graph-001", result.getId());
        assertEquals("测试图谱", result.getName());
        assertEquals(GraphBuildState.PENDING, result.getBuildStatus());
        assertEquals(0, result.getEntityCount());
        assertEquals(0, result.getRelationCount());

        verify(knowledgeGraphRepository).save(any(KnowledgeGraph.class));
    }

    @Test
    @DisplayName("F1.2 - 查询图谱：按 ID 查询返回正确结果")
    void testGetGraphById_Found() {
        // Given
        String graphId = "graph-001";
        KnowledgeGraph graph = KnowledgeGraph.builder()
                .id(graphId)
                .name("测试图谱")
                .build();

        when(knowledgeGraphRepository.findById(graphId)).thenReturn(Optional.of(graph));

        // When
        Optional<KnowledgeGraph> result = knowledgeGraphService.getGraphById(graphId);

        // Then
        assertTrue(result.isPresent());
        assertEquals("测试图谱", result.get().getName());
    }

    @Test
    @DisplayName("F1.2 - 查询图谱：不存在时返回空 Optional")
    void testGetGraphById_NotFound() {
        // Given
        when(knowledgeGraphRepository.findById("non-existent")).thenReturn(Optional.empty());

        // When
        Optional<KnowledgeGraph> result = knowledgeGraphService.getGraphById("non-existent");

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("F1.2 - 查询全部图谱：返回完整列表")
    void testGetAllGraphs() {
        // Given
        KnowledgeGraph g1 = KnowledgeGraph.builder().id("g1").name("图谱1").build();
        KnowledgeGraph g2 = KnowledgeGraph.builder().id("g2").name("图谱2").build();
        when(knowledgeGraphRepository.findAll()).thenReturn(Arrays.asList(g1, g2));

        // When
        List<KnowledgeGraph> result = knowledgeGraphService.getAllGraphs();

        // Then
        assertEquals(2, result.size());
        assertEquals("图谱1", result.get(0).getName());
    }

    @Test
    @DisplayName("F1.2 - 更新图谱：只更新非空字段")
    void testUpdateGraph_PartialUpdate() {
        // Given
        String graphId = "graph-001";
        KnowledgeGraph existing = KnowledgeGraph.builder()
                .id(graphId)
                .name("旧名称")
                .description("旧描述")
                .build();

        KnowledgeGraph updates = KnowledgeGraph.builder()
                .name("新名称")
                .build();

        when(knowledgeGraphRepository.findById(graphId)).thenReturn(Optional.of(existing));
        when(knowledgeGraphRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        KnowledgeGraph result = knowledgeGraphService.updateGraph(graphId, updates);

        // Then
        assertEquals("新名称", result.getName());
        assertEquals("旧描述", result.getDescription()); // 未更新的字段保持不变
    }

    @Test
    @DisplayName("F1.2 - 更新图谱：图谱不存在时抛出异常")
    void testUpdateGraph_NotFound() {
        // Given
        when(knowledgeGraphRepository.findById("non-existent")).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> knowledgeGraphService.updateGraph("non-existent", new KnowledgeGraph()));
    }

    @Test
    @DisplayName("F1.2 - 删除图谱：级联删除实体和关系")
    void testDeleteGraph_CascadeDelete() {
        // Given
        String graphId = "graph-001";

        // When
        knowledgeGraphService.deleteGraph(graphId);

        // Then
        verify(graphEntityRepository).deleteByGraphId(graphId);
        verify(graphRelationRepository).deleteByGraphId(graphId);
        verify(knowledgeGraphRepository).deleteById(graphId);
    }

    // ==================== 构建触发（伪完成） ====================

    @Test
    @DisplayName("F1.2 - 触发构建：仅修改状态，无实际构建逻辑")
    void testTriggerBuild_OnlyUpdatesStatus() {
        // Given
        String graphId = "graph-001";
        KnowledgeGraph existing = KnowledgeGraph.builder()
                .id(graphId)
                .name("测试图谱")
                .build();

        when(knowledgeGraphRepository.findById(graphId)).thenReturn(Optional.of(existing));
        when(knowledgeGraphRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        KnowledgeGraph result = knowledgeGraphService.triggerBuild(graphId);

        // Then
        assertEquals(GraphBuildState.BUILDING, result.getBuildStatus());
        // 注意：这里没有实际构建任务，没有异步操作，没有 LLM 调用
        // 这验证了 E2.3 中发现的"伪完成"状态
        verify(knowledgeGraphRepository, times(1)).save(any());
        verifyNoInteractions(graphEntityRepository);
        verifyNoInteractions(graphRelationRepository);
    }

    @Test
    @DisplayName("F1.2 - 触发构建：图谱不存在时抛出异常")
    void testTriggerBuild_NotFound() {
        // Given
        when(knowledgeGraphRepository.findById("non-existent")).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class,
                () -> knowledgeGraphService.triggerBuild("non-existent"));
    }

    // ==================== 实体/关系查询 ====================

    @Test
    @DisplayName("F1.2 - 查询实体列表：分页返回")
    void testGetGraphEntities_Paged() {
        // Given
        String graphId = "graph-001";
        Pageable pageable = PageRequest.of(0, 50);

        GraphEntity e1 = GraphEntity.builder()
                .id("e1")
                .graphId(graphId)
                .name("实体1")
                .entityType("人物")
                .build();

        GraphEntity e2 = GraphEntity.builder()
                .id("e2")
                .graphId(graphId)
                .name("实体2")
                .entityType("地点")
                .build();

        Page<GraphEntity> page = new PageImpl<>(Arrays.asList(e1, e2), pageable, 2);
        when(graphEntityRepository.findByGraphId(eq(graphId), any(Pageable.class))).thenReturn(page);

        // When
        Page<GraphEntity> result = knowledgeGraphService.getGraphEntities(graphId, pageable);

        // Then
        assertEquals(2, result.getContent().size());
        assertEquals(2, result.getTotalElements());
        assertEquals("实体1", result.getContent().get(0).getName());
    }

    @Test
    @DisplayName("F1.2 - 查询关系列表：分页返回")
    void testGetGraphRelations_Paged() {
        // Given
        String graphId = "graph-001";
        Pageable pageable = PageRequest.of(0, 50);

        GraphRelation r1 = GraphRelation.builder()
                .id("r1")
                .graphId(graphId)
                .sourceEntityId("e1")
                .targetEntityId("e2")
                .relationType("工作于")
                .weight(0.95)
                .build();

        Page<GraphRelation> page = new PageImpl<>(Collections.singletonList(r1), pageable, 1);
        when(graphRelationRepository.findByGraphId(eq(graphId), any(Pageable.class))).thenReturn(page);

        // When
        Page<GraphRelation> result = knowledgeGraphService.getGraphRelations(graphId, pageable);

        // Then
        assertEquals(1, result.getContent().size());
        assertEquals("工作于", result.getContent().get(0).getRelationType());
    }

    @Test
    @DisplayName("F1.2 - 搜索实体：按名称模糊匹配")
    void testSearchEntities_ByName() {
        // Given
        String graphId = "graph-001";
        GraphEntity e1 = GraphEntity.builder()
                .id("e1")
                .name("张三")
                .entityType("人物")
                .build();

        when(graphEntityRepository.findByGraphIdAndNameContainingIgnoreCase(graphId, "张"))
                .thenReturn(Collections.singletonList(e1));

        // When
        List<GraphEntity> result = knowledgeGraphService.searchEntities(graphId, "张");

        // Then
        assertEquals(1, result.size());
        assertEquals("张三", result.get(0).getName());
    }

    @Test
    @DisplayName("F1.2 - 搜索实体：无匹配时返回空列表")
    void testSearchEntities_NoMatch() {
        // Given
        when(graphEntityRepository.findByGraphIdAndNameContainingIgnoreCase(anyString(), anyString()))
                .thenReturn(Collections.emptyList());

        // When
        List<GraphEntity> result = knowledgeGraphService.searchEntities("graph-001", "不存在的名称");

        // Then
        assertTrue(result.isEmpty());
    }

    // ==================== 统计更新 ====================

    @Test
    @DisplayName("F1.2 - 更新统计：正确汇总实体和关系数量")
    void testUpdateGraphStats() {
        // Given
        String graphId = "graph-001";
        KnowledgeGraph graph = KnowledgeGraph.builder()
                .id(graphId)
                .name("测试图谱")
                .entityCount(0)
                .relationCount(0)
                .build();

        when(knowledgeGraphRepository.findById(graphId)).thenReturn(Optional.of(graph));
        when(graphEntityRepository.countByGraphId(graphId)).thenReturn(10L);
        when(graphRelationRepository.countByGraphId(graphId)).thenReturn(5L);
        when(knowledgeGraphRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        knowledgeGraphService.updateGraphStats(graphId);

        // Then
        verify(graphEntityRepository).countByGraphId(graphId);
        verify(graphRelationRepository).countByGraphId(graphId);
        verify(knowledgeGraphRepository).save(argThat(g ->
                g.getEntityCount() == 10 && g.getRelationCount() == 5
        ));
    }

    @Test
    @DisplayName("F1.2 - 更新统计：图谱不存在时静默返回")
    void testUpdateGraphStats_NotFound() {
        // Given
        when(knowledgeGraphRepository.findById("non-existent")).thenReturn(Optional.empty());

        // When
        knowledgeGraphService.updateGraphStats("non-existent");

        // Then: 不抛异常，不保存
        verify(knowledgeGraphRepository, never()).save(any());
    }
}
