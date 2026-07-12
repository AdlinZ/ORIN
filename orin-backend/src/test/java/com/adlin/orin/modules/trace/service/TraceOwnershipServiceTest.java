package com.adlin.orin.modules.trace.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.audit.repository.AuditLogRepository;
import com.adlin.orin.modules.collaboration.repository.CollaborationPackageRepository;
import com.adlin.orin.modules.workflow.entity.WorkflowInstanceEntity;
import com.adlin.orin.modules.workflow.repository.WorkflowInstanceRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class TraceOwnershipServiceTest {

    private WorkflowInstanceRepository instanceRepository;
    private CollaborationPackageRepository collaborationPackageRepository;
    private AuditLogRepository auditLogRepository;
    private TraceOwnershipService service;

    @BeforeEach
    void setUp() {
        instanceRepository = mock(WorkflowInstanceRepository.class);
        collaborationPackageRepository = mock(CollaborationPackageRepository.class);
        auditLogRepository = mock(AuditLogRepository.class);
        service = new TraceOwnershipService(
                instanceRepository, collaborationPackageRepository, auditLogRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void assertCanReadInstanceOwnerPasses() {
        authenticate("42", "ROLE_USER");
        when(instanceRepository.findById(100L))
                .thenReturn(Optional.of(WorkflowInstanceEntity.builder().id(100L).userId(42L).build()));
        assertDoesNotThrow(() -> service.assertCanReadInstance(100L));
    }

    @Test
    void assertCanReadInstanceNonOwnerThrowsForbidden() {
        authenticate("42", "ROLE_USER");
        when(instanceRepository.findById(101L))
                .thenReturn(Optional.of(WorkflowInstanceEntity.builder().id(101L).userId(99L).build()));
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.assertCanReadInstance(101L));
        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
    }

    @Test
    void assertCanReadTraceByWorkflowInstanceOwnershipPasses() {
        authenticate("42", "ROLE_USER");
        when(instanceRepository.findByTraceId("trace-1"))
                .thenReturn(Optional.of(WorkflowInstanceEntity.builder().id(1L).userId(42L).build()));
        assertDoesNotThrow(() -> service.assertCanReadTrace("trace-1"));
    }

    @Test
    void assertCanReadTraceNoOwnershipThrowsForbidden() {
        authenticate("42", "ROLE_USER");
        when(instanceRepository.findByTraceId("trace-orphan")).thenReturn(Optional.empty());
        when(collaborationPackageRepository.findByTraceId("trace-orphan")).thenReturn(List.of());
        when(auditLogRepository.findByTraceIdOrderByCreatedAtAsc("trace-orphan")).thenReturn(List.of());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.assertCanReadTrace("trace-orphan"));
        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
    }

    @Test
    void assertCanReadTraceAdminBypassesOwnership() {
        authenticate("1", "ROLE_ADMIN");
        when(instanceRepository.findByTraceId("trace-any")).thenReturn(Optional.empty());
        when(collaborationPackageRepository.findByTraceId("trace-any")).thenReturn(List.of());
        when(auditLogRepository.findByTraceIdOrderByCreatedAtAsc("trace-any")).thenReturn(List.of());
        assertDoesNotThrow(() -> service.assertCanReadTrace("trace-any"));
    }

    private void authenticate(String principal, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority(role))));
    }
}