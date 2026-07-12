package com.adlin.orin.modules.system.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.modules.system.dto.RegisterDTO;
import com.adlin.orin.modules.system.entity.SysRole;
import com.adlin.orin.modules.system.entity.SysUser;
import com.adlin.orin.modules.system.entity.SysUserRole;
import com.adlin.orin.modules.system.repository.SysRoleRepository;
import com.adlin.orin.modules.system.repository.SysUserRepository;
import com.adlin.orin.modules.system.repository.SysUserRoleRepository;
import com.adlin.orin.modules.system.service.dto.AuthResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private SysUserRepository userRepository;
    private SysRoleRepository roleRepository;
    private SysUserRoleRepository userRoleRepository;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(SysUserRepository.class);
        roleRepository = mock(SysRoleRepository.class);
        userRoleRepository = mock(SysUserRoleRepository.class);

        authService = new AuthService();
        ReflectionTestUtils.setField(authService, "userRepository", userRepository);
        ReflectionTestUtils.setField(authService, "userRoleService", mock(UserRoleService.class));
        ReflectionTestUtils.setField(authService, "roleRepository", roleRepository);
        ReflectionTestUtils.setField(authService, "userRoleRepository", userRoleRepository);
        ReflectionTestUtils.setField(authService, "passwordEncoder", new BCryptPasswordEncoder());
        ReflectionTestUtils.setField(authService, "selfRegistrationEnabled", true);
    }

    @Test
    void registerCreatesEnabledRoleUserWithRoleJoin() {
        RegisterDTO request = new RegisterDTO();
        request.setUsername(" New.User ");
        request.setPassword("StrongPass123");
        request.setNickname("New User");
        request.setEmail(" User@Example.com ");

        SysRole role = SysRole.builder()
                .roleId(7L)
                .roleCode("ROLE_USER")
                .roleName("普通用户")
                .build();
        when(roleRepository.findByRoleCode("ROLE_USER")).thenReturn(Optional.of(role));
        when(userRepository.findByUsername("new.user")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(SysUser.class))).thenAnswer(invocation -> {
            SysUser saved = invocation.getArgument(0);
            saved.setUserId(42L);
            return saved;
        });

        AuthResult result = authService.register(request);

        assertThat(result.user().getUsername()).isEqualTo("new.user");
        assertThat(result.user().getEmail()).isEqualTo("user@example.com");
        assertThat(result.user().getStatus()).isEqualTo("ENABLED");
        assertThat(result.user().getRole()).isEqualTo("ROLE_USER");
        assertThat(result.user().getPassword()).isNotEqualTo("StrongPass123");
        assertThat(new BCryptPasswordEncoder().matches("StrongPass123", result.user().getPassword())).isTrue();
        assertThat(result.roles()).containsExactly("ROLE_USER");

        ArgumentCaptor<SysUserRole> userRoleCaptor = ArgumentCaptor.forClass(SysUserRole.class);
        verify(userRoleRepository).save(userRoleCaptor.capture());
        assertThat(userRoleCaptor.getValue().getUserId()).isEqualTo(42L);
        assertThat(userRoleCaptor.getValue().getRoleId()).isEqualTo(7L);
    }

    @Test
    void registerRejectsWhenSelfRegistrationDisabled() {
        ReflectionTestUtils.setField(authService, "selfRegistrationEnabled", false);

        RegisterDTO request = new RegisterDTO();
        request.setUsername("new-user");
        request.setPassword("StrongPass123");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未开放自助注册");
    }

    @Test
    void registerRejectsDuplicateUsername() {
        RegisterDTO request = new RegisterDTO();
        request.setUsername("new-user");
        request.setPassword("StrongPass123");

        when(userRepository.findByUsername("new-user")).thenReturn(Optional.of(new SysUser()));

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名已存在");
    }
}
