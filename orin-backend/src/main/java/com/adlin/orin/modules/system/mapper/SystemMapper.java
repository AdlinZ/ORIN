package com.adlin.orin.modules.system.mapper;

import com.adlin.orin.modules.system.dto.LoginDTO;
import com.adlin.orin.modules.system.entity.SysRole;
import com.adlin.orin.modules.system.entity.SysUser;
import org.mapstruct.*;

import java.util.List;

/**
 * 系统实体与DTO转换Mapper
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface SystemMapper {

    /**
     * LoginDTO转SysUser（仅用于验证，不创建实体）
     * 主要用于从LoginDTO提取用户名密码
     */
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "nickname", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "avatar", ignore = true)
    SysUser loginDtoToUser(LoginDTO loginDTO);

    /**
     * SysUser转UserResponse（如果需要返回用户信息）
     */
    UserResponse toUserResponse(SysUser user);

    /**
     * SysRole转RoleResponse
     */
    RoleResponse toRoleResponse(SysRole role);

    /**
     * SysRole列表转RoleResponse列表
     */
    List<RoleResponse> toRoleResponseList(List<SysRole> roles);

    /**
     * 用户响应DTO（内部类定义）
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class UserResponse {
        private Long userId;
        private String username;
        private String nickname;
        private String email;
        private String status;
    }

    /**
     * 角色响应DTO（内部类定义）
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class RoleResponse {
        private Long roleId;
        private String roleCode;
        private String roleName;
        private String description;
    }
}
