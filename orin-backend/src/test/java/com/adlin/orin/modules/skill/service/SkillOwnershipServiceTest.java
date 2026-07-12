package com.adlin.orin.modules.skill.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.skill.entity.SkillEntity;
import com.adlin.orin.modules.skill.repository.SkillRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class SkillOwnershipServiceTest {

    private SkillRepository repository;
    private SkillOwnershipService service;

    @BeforeEach
    void setUp() {
        repository = mock(SkillRepository.class);
        service = new SkillOwnershipService(repository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void canReadReturnsTrueForSystemSkill() {
        authenticate("42", "ROLE_USER");
        assertTrue(service.canRead("system"));
    }

    @Test
    void canReadReturnsTrueForOwner() {
        authenticate("42", "ROLE_USER");
        assertTrue(service.canRead("42"));
    }

    @Test
    void canReadReturnsFalseForOtherUser() {
        authenticate("42", "ROLE_USER");
        assertFalse(service.canRead("99"));
    }

    @Test
    void assertCanReadOwnerPasses() {
        authenticate("42", "ROLE_USER");
        when(repository.findById(1L)).thenReturn(Optional.of(skill(1L, "42", SkillEntity.SkillType.API)));
        assertDoesNotThrow(() -> service.assertCanRead(1L));
    }

    @Test
    void assertCanReadNonOwnerThrowsForbidden() {
        authenticate("42", "ROLE_USER");
        when(repository.findById(2L)).thenReturn(Optional.of(skill(2L, "99", SkillEntity.SkillType.API)));
        BusinessException error = assertThrows(BusinessException.class, () -> service.assertCanRead(2L));
        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
    }

    @Test
    void assertCanCreateTypeShellByNonAdminThrowsForbidden() {
        authenticate("42", "ROLE_USER");
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.assertCanCreateType(SkillEntity.SkillType.SHELL));
        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
    }

    @Test
    void assertCanCreateTypeApiByNonAdminThrowsForbidden() {
        authenticate("42", "ROLE_USER");
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.assertCanCreateType(SkillEntity.SkillType.API));
        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
    }

    @Test
    void assertCanCreateTypeShellByAdminPasses() {
        authenticate("1", "ROLE_ADMIN");
        assertDoesNotThrow(() -> service.assertCanCreateType(SkillEntity.SkillType.SHELL));
    }

    @Test
    void assertCanExecuteShellByNonAdminThrowsForbidden() {
        authenticate("42", "ROLE_USER");
        when(repository.findById(3L))
                .thenReturn(Optional.of(skill(3L, "42", SkillEntity.SkillType.SHELL)));
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.assertCanExecute(3L));
        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
    }

    @Test
    void assertCanExecuteApiByOwnerPasses() {
        authenticate("42", "ROLE_USER");
        when(repository.findById(4L))
                .thenReturn(Optional.of(skill(4L, "42", SkillEntity.SkillType.API)));
        assertDoesNotThrow(() -> service.assertCanExecute(4L));
    }

    private void authenticate(String principal, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority(role))));
    }

    private SkillEntity skill(Long id, String createdBy, SkillEntity.SkillType type) {
        return SkillEntity.builder()
                .id(id)
                .skillName("skill-" + id)
                .skillType(type)
                .createdBy(createdBy)
                .build();
    }
}