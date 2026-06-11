package com.adlin.orin.modules.workflow.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.modules.workflow.entity.WorkflowEntity;
import com.adlin.orin.modules.workflow.entity.WorkflowInstanceEntity;
import com.adlin.orin.modules.workflow.converter.DifyDslConverter;
import com.adlin.orin.modules.workflow.dsl.OrinWorkflowDslNormalizer;
import com.adlin.orin.modules.workflow.dsl.OrinWorkflowDslValidator;
import com.adlin.orin.modules.workflow.engine.WorkflowEngine;
import com.adlin.orin.modules.workflow.repository.WorkflowInstanceRepository;
import com.adlin.orin.modules.workflow.repository.WorkflowRepository;
import com.adlin.orin.modules.workflow.repository.WorkflowStepRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 资源级 ACL 第 3 刀 (Workflow) 行为测试。
 *
 * 覆盖:
 * - getAllWorkflows: admin / operator / 普通用户 读路径
 * - updateWorkflow / publishWorkflow / archiveWorkflow / deleteWorkflow: 非 owner 抛 FORBIDDEN
 * - getWorkflowById: 非 owner 抛 FORBIDDEN
 * - getInstance: 非 owner 抛 FORBIDDEN (instance 跟随所属 workflow)
 * - getWorkflowInstances: 非 owner 抛 FORBIDDEN (instance 跟随所属 workflow)
 *
 * submitExecution 涉及太多协作/任务依赖, 单测成本过高, 留待协作/任务模块统一补
 *
 * 运行方式: mvn test -Dtest=WorkflowServiceAclTest
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowServiceAclTest {

    private static final Long USER_ID = 100L;
    private static final Long OTHER_USER_ID = 200L;
    private static final Long WORKFLOW_ID = 1L;
    private static final Long OTHER_WORKFLOW_ID = 2L;
    private static final Long INSTANCE_ID = 10L;

    @Mock private WorkflowRepository workflowRepository;
    @Mock private WorkflowStepRepository stepRepository;
    @Mock private WorkflowInstanceRepository instanceRepository;
    @Mock private WorkflowEngine workflowEngine;
    @Mock private DifyDslConverter difyDslConverter;
    @Mock private OrinWorkflowDslNormalizer workflowDslNormalizer;
    @Mock private OrinWorkflowDslValidator workflowDslValidator;
    @Mock private com.adlin.orin.modules.task.service.TaskService taskService;
    @Mock private WorkflowOwnershipResolver workflowOwnershipResolver;

    @InjectMocks private WorkflowService service;

    @BeforeEach
    void setUp() {
        // DSL 校验链路松绑(本测试不关注 DSL 合法性,只关注 ACL 行为)
        lenient().doReturn(java.util.Collections.<String, Object>emptyMap())
                .when(workflowDslNormalizer).normalize(any(), anyString());
        lenient().doNothing().when(workflowDslValidator).validateForPublishOrThrow(any());
    }

    // ============================================================
    // getAllWorkflows 读路径
    // ============================================================

    @Test
    @DisplayName("ACL-read: 普通用户 getAll 仅按 owner 过滤")
    void getAll_regularUser_filtersByOwner() {
        when(workflowOwnershipResolver.isCurrentUserPrivileged()).thenReturn(false);
        when(workflowOwnershipResolver.resolveFromCurrentRequest()).thenReturn(USER_ID);
        when(workflowRepository.findByOwnerUserId(USER_ID))
                .thenReturn(List.of(workflow(WORKFLOW_ID, USER_ID)));

        var result = service.getAllWorkflows();

        assertEquals(1, result.size());
        verify(workflowRepository).findByOwnerUserId(USER_ID);
        verify(workflowRepository, never()).findAll();
    }

    @Test
    @DisplayName("ACL-read: 管理员 getAll 走 findAll,不受 owner 限制")
    void getAll_admin_seesAll() {
        when(workflowOwnershipResolver.isCurrentUserPrivileged()).thenReturn(true);
        when(workflowRepository.findAll()).thenReturn(List.of(
                workflow(WORKFLOW_ID, USER_ID),
                workflow(OTHER_WORKFLOW_ID, OTHER_USER_ID)));

        var result = service.getAllWorkflows();

        assertEquals(2, result.size());
        verify(workflowRepository).findAll();
        verify(workflowRepository, never()).findByOwnerUserId(any());
    }

    // ============================================================
    // 写路径
    // ============================================================

    @Test
    @DisplayName("ACL-update: 非 owner 调用 update 抛 FORBIDDEN,不进入字段更新")
    void update_nonOwner_throwsForbidden() {
        WorkflowEntity existing = workflow(WORKFLOW_ID, OTHER_USER_ID);
        when(workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(existing));
        doThrow(new BusinessException(com.adlin.orin.common.exception.ErrorCode.FORBIDDEN, "无权操作该工作流"))
                .when(workflowOwnershipResolver).assertCanManage(existing);

        com.adlin.orin.modules.workflow.dto.WorkflowRequest request =
                new com.adlin.orin.modules.workflow.dto.WorkflowRequest();
        request.setWorkflowName("hacked");

        assertThrows(BusinessException.class,
                () -> service.updateWorkflow(WORKFLOW_ID, request));
        verify(workflowRepository, never()).save(any());
    }

    @Test
    @DisplayName("ACL-publish: 非 owner 调用 publish 抛 FORBIDDEN")
    void publish_nonOwner_throwsForbidden() {
        WorkflowEntity existing = workflow(WORKFLOW_ID, OTHER_USER_ID);
        when(workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(existing));
        doThrow(new BusinessException(com.adlin.orin.common.exception.ErrorCode.FORBIDDEN, "无权操作该工作流"))
                .when(workflowOwnershipResolver).assertCanManage(existing);

        assertThrows(BusinessException.class, () -> service.publishWorkflow(WORKFLOW_ID));
        verify(workflowDslValidator, never()).validateForPublishOrThrow(any());
        verify(workflowRepository, never()).save(any());
    }

    @Test
    @DisplayName("ACL-archive: 非 owner 调用 archive 抛 FORBIDDEN,不进入状态变更")
    void archive_nonOwner_throwsForbidden() {
        WorkflowEntity existing = workflow(WORKFLOW_ID, OTHER_USER_ID);
        when(workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(existing));
        doThrow(new BusinessException(com.adlin.orin.common.exception.ErrorCode.FORBIDDEN, "无权操作该工作流"))
                .when(workflowOwnershipResolver).assertCanManage(existing);

        assertThrows(BusinessException.class, () -> service.archiveWorkflow(WORKFLOW_ID));
        verify(workflowRepository, never()).save(any());
    }

    @Test
    @DisplayName("ACL-delete: 非 owner 调用 delete 抛 FORBIDDEN,不进入删除流程")
    void delete_nonOwner_throwsForbidden() {
        WorkflowEntity existing = workflow(WORKFLOW_ID, OTHER_USER_ID);
        when(workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(existing));
        doThrow(new BusinessException(com.adlin.orin.common.exception.ErrorCode.FORBIDDEN, "无权操作该工作流"))
                .when(workflowOwnershipResolver).assertCanManage(existing);

        assertThrows(BusinessException.class, () -> service.deleteWorkflow(WORKFLOW_ID));
        verify(stepRepository, never()).deleteByWorkflowId(anyLong());
        verify(instanceRepository, never()).deleteByWorkflowId(anyLong());
        verify(workflowRepository, never()).deleteById(any());
    }

    // ============================================================
    // 读路径 + instance 跟随
    // ============================================================

    @Test
    @DisplayName("ACL-getById: 非 owner 调用 getById 抛 FORBIDDEN")
    void getById_nonOwner_throwsForbidden() {
        WorkflowEntity existing = workflow(WORKFLOW_ID, OTHER_USER_ID);
        when(workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(existing));
        doThrow(new BusinessException(com.adlin.orin.common.exception.ErrorCode.FORBIDDEN, "无权操作该工作流"))
                .when(workflowOwnershipResolver).assertCanManage(existing);

        assertThrows(BusinessException.class, () -> service.getWorkflowById(WORKFLOW_ID));
    }

    @Test
    @DisplayName("ACL-getInstance: instance 跟随所属 workflow,非 owner 抛 FORBIDDEN")
    void getInstance_followsWorkflowOwner_throwsForbidden() {
        WorkflowInstanceEntity instance = WorkflowInstanceEntity.builder()
                .id(INSTANCE_ID)
                .workflowId(OTHER_WORKFLOW_ID)
                .build();
        WorkflowEntity ownerWorkflow = workflow(OTHER_WORKFLOW_ID, OTHER_USER_ID);
        when(instanceRepository.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));
        when(workflowRepository.findById(OTHER_WORKFLOW_ID)).thenReturn(Optional.of(ownerWorkflow));
        doThrow(new BusinessException(com.adlin.orin.common.exception.ErrorCode.FORBIDDEN, "无权操作该工作流"))
                .when(workflowOwnershipResolver).assertCanManage(ownerWorkflow);

        assertThrows(BusinessException.class, () -> service.getInstance(INSTANCE_ID));
    }

    @Test
    @DisplayName("ACL-getInstances: instance 跟随所属 workflow,非 owner 抛 FORBIDDEN")
    void getInstances_followsWorkflowOwner_throwsForbidden() {
        WorkflowEntity ownerWorkflow = workflow(OTHER_WORKFLOW_ID, OTHER_USER_ID);
        when(workflowRepository.findById(OTHER_WORKFLOW_ID)).thenReturn(Optional.of(ownerWorkflow));
        doThrow(new BusinessException(com.adlin.orin.common.exception.ErrorCode.FORBIDDEN, "无权操作该工作流"))
                .when(workflowOwnershipResolver).assertCanManage(ownerWorkflow);

        assertThrows(BusinessException.class, () -> service.getWorkflowInstances(OTHER_WORKFLOW_ID));
        verify(instanceRepository, never()).findByWorkflowIdOrderByStartedAtDesc(anyLong());
    }

    // ============================================================
    // helpers
    // ============================================================

    private WorkflowEntity workflow(Long id, Long ownerUserId) {
        return WorkflowEntity.builder()
                .id(id)
                .workflowName("wf-" + id)
                .ownerUserId(ownerUserId)
                .status(WorkflowEntity.WorkflowStatus.DRAFT)
                .workflowType(WorkflowEntity.WorkflowType.DAG)
                .build();
    }
}
