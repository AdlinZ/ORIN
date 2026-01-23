package com.adlin.orin.modules.system.mapper;

import com.adlin.orin.modules.system.dto.LoginDTO;
import com.adlin.orin.modules.system.entity.SysRole;
import com.adlin.orin.modules.system.entity.SysUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SystemMapper 单元测试
 */
class SystemMapperTest {

    private SystemMapper systemMapper;

    @BeforeEach
    void setUp() {
        systemMapper = Mappers.getMapper(SystemMapper.class);
    }

    @Test
    void testLoginDtoToUser() {
        // Given
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername("testuser");
        loginDTO.setPassword("testpass");

        // When
        SysUser user = systemMapper.loginDtoToUser(loginDTO);

        // Then
        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo("testuser");
        assertThat(user.getPassword()).isEqualTo("testpass");
    }

    @Test
    void testToUserResponse() {
        // Given
        SysUser user = new SysUser();
        user.setUserId(1L);
        user.setUsername("admin");
        user.setNickname("Administrator");
        user.setEmail("admin@test.com");
        user.setStatus("ENABLED");
        user.setPassword("secret"); // Should be ignored

        // When
        SystemMapper.UserResponse response = systemMapper.toUserResponse(user);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getUsername()).isEqualTo("admin");
        assertThat(response.getNickname()).isEqualTo("Administrator");
        assertThat(response.getEmail()).isEqualTo("admin@test.com");
        assertThat(response.getStatus()).isEqualTo("ENABLED");
    }

    @Test
    void testToRoleResponse() {
        // Given
        SysRole role = SysRole.builder()
                .roleId(1L)
                .roleCode("ROLE_ADMIN")
                .roleName("Administrator")
                .description("System Administrator")
                .build();

        // When
        SystemMapper.RoleResponse response = systemMapper.toRoleResponse(role);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getRoleId()).isEqualTo(1L);
        assertThat(response.getRoleCode()).isEqualTo("ROLE_ADMIN");
        assertThat(response.getRoleName()).isEqualTo("Administrator");
        assertThat(response.getDescription()).isEqualTo("System Administrator");
    }
}
