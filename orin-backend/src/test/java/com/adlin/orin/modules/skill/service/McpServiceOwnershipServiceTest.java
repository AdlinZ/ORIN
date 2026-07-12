package com.adlin.orin.modules.skill.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.skill.entity.McpService;
import com.adlin.orin.modules.skill.repository.McpServiceRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class McpServiceOwnershipServiceTest {

    private McpServiceRepository repository;
    private McpServiceOwnershipService service;

    @BeforeEach
    void setUp() {
        repository = mock(McpServiceRepository.class);
        service = new McpServiceOwnershipService(repository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void visibleServicesPrivilegedReturnsAll() {
        authenticate("1", "ROLE_ADMIN");
        McpService platform = mcpService(10L, null);
        McpService ownedByOther = mcpService(11L, 99L);
        when(repository.findAll()).thenReturn(List.of(platform, ownedByOther));

        List<McpService> visible = service.visibleServices();
        assertEquals(2, visible.size());
    }

    @Test
    void visibleServicesNonPrivilegedReturnsOwnedAndNullOwner() {
        authenticate("42", "ROLE_USER");
        McpService own = mcpService(20L, 42L);
        McpService platform = mcpService(21L, null);
        when(repository.findByOwnerUserIdOrOwnerUserIdIsNull(42L))
                .thenReturn(List.of(own, platform));

        List<McpService> visible = service.visibleServices();
        assertEquals(2, visible.size());
        assertSame(own, visible.get(0));
        assertSame(platform, visible.get(1));
        // 99号用户的记录由仓库过滤掉，无需出现在 mock 返回里
    }

    @Test
    void assertCanManageOwnerSucceeds() {
        authenticate("42", "ROLE_USER");
        McpService own = mcpService(30L, 42L);
        assertDoesNotThrow(() -> service.assertCanManage(own));
    }

    @Test
    void assertCanManageNonOwnerThrowsForbidden() {
        authenticate("42", "ROLE_USER");
        McpService other = mcpService(31L, 99L);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.assertCanManage(other));
        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
    }

    @Test
    void assertCanManageNullOwnerNonAdminThrowsForbidden() {
        authenticate("42", "ROLE_USER");
        McpService platform = mcpService(32L, null);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.assertCanManage(platform));
        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
    }

    @Test
    void assignOwnerForCreateAdminLeavesOwnerNull() {
        authenticate("1", "ROLE_ADMIN");
        McpService toCreate = new McpService();
        service.assignOwnerForCreate(toCreate);
        assertNull(toCreate.getOwnerUserId());
    }

    @Test
    void assignOwnerForCreateNonAdminStampsCurrentUser() {
        authenticate("42", "ROLE_USER");
        McpService toCreate = new McpService();
        service.assignOwnerForCreate(toCreate);
        assertEquals(42L, toCreate.getOwnerUserId());
    }

    @Test
    void assertCanUseIdsMixedOwnershipAllowsOwnAndBlocksOther() {
        authenticate("42", "ROLE_USER");
        McpService own = mcpService(40L, 42L);
        McpService other = mcpService(41L, 99L);
        when(repository.findById(40L)).thenReturn(Optional.of(own));
        when(repository.findById(41L)).thenReturn(Optional.of(other));

        assertDoesNotThrow(() -> service.assertCanUseIds(List.of(40L)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.assertCanUseIds(List.of(40L, 41L)));
        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
    }

    private void authenticate(String principal, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority(role))));
    }

    private McpService mcpService(Long id, Long ownerUserId) {
        return McpService.builder()
                .id(id)
                .ownerUserId(ownerUserId)
                .name("svc-" + id)
                .toolKey("tk-" + id)
                .build();
    }
}