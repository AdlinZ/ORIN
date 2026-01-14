package com.adlin.orin.modules.system.service;

import com.adlin.orin.modules.system.entity.SysRole;
import com.adlin.orin.modules.system.repository.SysRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 角色管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private final SysRoleRepository roleRepository;

    /**
     * 获取所有角色
     */
    public List<SysRole> getAllRoles() {
        return roleRepository.findAll();
    }

    /**
     * 根据ID获取角色
     */
    public Optional<SysRole> getRoleById(Long roleId) {
        return roleRepository.findById(roleId);
    }

    /**
     * 根据角色代码获取角色
     */
    public Optional<SysRole> getRoleByCode(String roleCode) {
        return roleRepository.findByRoleCode(roleCode);
    }

    /**
     * 创建角色
     */
    @Transactional
    public SysRole createRole(SysRole role) {
        if (roleRepository.existsByRoleCode(role.getRoleCode())) {
            throw new RuntimeException("角色代码已存在: " + role.getRoleCode());
        }
        return roleRepository.save(role);
    }

    /**
     * 更新角色
     */
    @Transactional
    public SysRole updateRole(Long roleId, SysRole role) {
        SysRole existingRole = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("角色不存在"));

        existingRole.setRoleName(role.getRoleName());
        existingRole.setDescription(role.getDescription());

        return roleRepository.save(existingRole);
    }

    /**
     * 删除角色(系统预定义角色不可删除)
     */
    @Transactional
    public void deleteRole(Long roleId) {
        SysRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("角色不存在"));

        // 保护系统预定义角色
        if ("ROLE_ADMIN".equals(role.getRoleCode()) || "ROLE_USER".equals(role.getRoleCode())) {
            throw new RuntimeException("系统预定义角色不可删除");
        }

        roleRepository.deleteById(roleId);
    }

    /**
     * 初始化默认角色
     */
    @Transactional
    public void initializeDefaultRoles() {
        if (!roleRepository.existsByRoleCode("ROLE_ADMIN")) {
            SysRole adminRole = SysRole.builder()
                    .roleCode("ROLE_ADMIN")
                    .roleName("系统管理员")
                    .description("拥有系统所有权限")
                    .build();
            roleRepository.save(adminRole);
            log.info("Created default admin role");
        }

        if (!roleRepository.existsByRoleCode("ROLE_USER")) {
            SysRole userRole = SysRole.builder()
                    .roleCode("ROLE_USER")
                    .roleName("普通用户")
                    .description("基础访问权限")
                    .build();
            roleRepository.save(userRole);
            log.info("Created default user role");
        }
    }
}
