package com.adlin.orin.modules.system.service;

import com.adlin.orin.modules.system.entity.SysUserRole;
import com.adlin.orin.modules.system.repository.SysUserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户角色管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserRoleService {

    private final SysUserRoleRepository userRoleRepository;

    /**
     * 获取用户的所有角色
     */
    public List<SysUserRole> getUserRoles(Long userId) {
        return userRoleRepository.findByUserId(userId);
    }

    /**
     * 获取用户的角色代码列表
     */
    public List<String> getUserRoleCodes(Long userId) {
        return userRoleRepository.findRoleCodesByUserId(userId);
    }

    /**
     * 为用户分配角色
     */
    @Transactional
    public SysUserRole assignRole(Long userId, Long roleId) {
        if (userRoleRepository.existsByUserIdAndRoleId(userId, roleId)) {
            throw new RuntimeException("用户已拥有该角色");
        }

        SysUserRole userRole = SysUserRole.builder()
                .userId(userId)
                .roleId(roleId)
                .build();

        return userRoleRepository.save(userRole);
    }

    /**
     * 移除用户角色
     */
    @Transactional
    public void removeRole(Long userId, Long roleId) {
        userRoleRepository.deleteByUserIdAndRoleId(userId, roleId);
    }

    /**
     * 批量设置用户角色(先删除所有,再添加新的)
     */
    @Transactional
    public void setUserRoles(Long userId, List<Long> roleIds) {
        // 删除用户现有角色
        userRoleRepository.deleteByUserId(userId);

        // 添加新角色
        for (Long roleId : roleIds) {
            SysUserRole userRole = SysUserRole.builder()
                    .userId(userId)
                    .roleId(roleId)
                    .build();
            userRoleRepository.save(userRole);
        }
    }

    /**
     * 检查用户是否拥有指定角色
     */
    public boolean hasRole(Long userId, String roleCode) {
        List<String> roleCodes = getUserRoleCodes(userId);
        return roleCodes.contains(roleCode);
    }

    /**
     * 检查用户是否为管理员
     */
    public boolean isAdmin(Long userId) {
        return hasRole(userId, "ROLE_ADMIN");
    }
}
