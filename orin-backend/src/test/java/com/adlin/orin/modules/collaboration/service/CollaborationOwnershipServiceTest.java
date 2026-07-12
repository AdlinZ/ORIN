package com.adlin.orin.modules.collaboration.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.collaboration.entity.CollaborationPackageEntity;
import com.adlin.orin.modules.collaboration.repository.CollabSessionRepository;
import com.adlin.orin.modules.collaboration.repository.CollaborationPackageRepository;
import com.adlin.orin.modules.collaboration.repository.CollaborationTaskRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class CollaborationOwnershipServiceTest {

    private CollaborationPackageRepository packageRepository;
    private CollaborationOwnershipService service;

    @BeforeEach
    void setUp() {
        packageRepository = mock(CollaborationPackageRepository.class);
        service = new CollaborationOwnershipService(
                packageRepository,
                mock(CollaborationTaskRepository.class),
                mock(CollabSessionRepository.class));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownerCanManagePackageAndIdentityComesFromJwtPrincipal() {
        authenticate("42", "ROLE_USER");
        when(packageRepository.findByPackageId("pkg-owned"))
                .thenReturn(Optional.of(CollaborationPackageEntity.builder()
                        .packageId("pkg-owned")
                        .createdBy("42")
                        .build()));

        assertEquals("42", service.currentUserKey());
        assertDoesNotThrow(() -> service.assertCanManagePackage("pkg-owned"));
    }

    @Test
    void regularUserCannotManageAnotherUsersPackage() {
        authenticate("42", "ROLE_USER");
        when(packageRepository.findByPackageId("pkg-other"))
                .thenReturn(Optional.of(CollaborationPackageEntity.builder()
                        .packageId("pkg-other")
                        .createdBy("99")
                        .build()));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.assertCanManagePackage("pkg-other"));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
    }

    @Test
    void adminCanManageAnyPackage() {
        authenticate("1", "ROLE_ADMIN");
        when(packageRepository.findByPackageId("pkg-other"))
                .thenReturn(Optional.of(CollaborationPackageEntity.builder()
                        .packageId("pkg-other")
                        .createdBy("99")
                        .build()));

        assertDoesNotThrow(() -> service.assertCanManagePackage("pkg-other"));
    }

    private void authenticate(String principal, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority(role))));
    }
}
