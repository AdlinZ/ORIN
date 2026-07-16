package com.adlin.orin.modules.system.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.modules.system.entity.SysRole;
import com.adlin.orin.modules.system.repository.SysRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private SysRoleRepository roleRepository;

    private RoleService roleService;

    @BeforeEach
    void setUp() {
        roleService = new RoleService(roleRepository);
    }

    @Test
    void searchesBeforePaginationWithTrimmedQuery() {
        PageRequest pageable = PageRequest.of(0, 20);
        SysRole role = SysRole.builder()
                .roleId(3L)
                .roleCode("ROLE_OPERATOR")
                .roleName("业务运营")
                .build();
        PageImpl<SysRole> page = new PageImpl<>(List.of(role), pageable, 1);
        when(roleRepository.findAllFiltered("运营", pageable)).thenReturn(page);

        assertEquals(page, roleService.searchRoles(" 运营 ", pageable));
        verify(roleRepository).findAllFiltered("运营", pageable);
    }

    @Test
    void convertsBlankSearchToNull() {
        PageRequest pageable = PageRequest.of(0, 20);
        PageImpl<SysRole> page = new PageImpl<>(List.of(), pageable, 0);
        when(roleRepository.findAllFiltered(null, pageable)).thenReturn(page);

        assertEquals(page, roleService.searchRoles("  ", pageable));
        verify(roleRepository).findAllFiltered(null, pageable);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ROLE_ADMIN",
            "ROLE_USER",
            "ROLE_OPERATOR",
            "ROLE_PLATFORM_ADMIN",
            "ROLE_SUPER_ADMIN"
    })
    void refusesToDeleteEverySystemRole(String roleCode) {
        SysRole role = SysRole.builder()
                .roleId(8L)
                .roleCode(roleCode)
                .roleName("system role")
                .build();
        when(roleRepository.findById(8L)).thenReturn(java.util.Optional.of(role));

        assertThrows(BusinessException.class, () -> roleService.deleteRole(8L));

        verify(roleRepository, never()).deleteById(8L);
    }
}
