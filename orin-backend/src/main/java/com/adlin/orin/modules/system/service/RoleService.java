package com.adlin.orin.modules.system.service;

import com.adlin.orin.modules.system.entity.SysRole;
import com.adlin.orin.modules.system.repository.SysRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 角色管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private final SysRoleRepository roleRepository;
    private static final Set<String> SYSTEM_ROLE_CODES = Set.of(
            "ROLE_ADMIN",
            "ROLE_USER",
            "ROLE_OPERATOR",
            "ROLE_PLATFORM_ADMIN",
            "ROLE_SUPER_ADMIN"
    );

    /**
     * 获取所有角色
     */
    public List<SysRole> getAllRoles() {
        return roleRepository.findAll();
    }

    /**
     * 分页获取角色
     */
    public Page<SysRole> getAllRolesPageable(Pageable pageable) {
        return roleRepository.findAll(pageable);
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
        if (SYSTEM_ROLE_CODES.contains(role.getRoleCode())) {
            throw new RuntimeException("系统预定义角色不可删除");
        }

        roleRepository.deleteById(roleId);
    }

    /**
     * 初始化默认角色
     */
    @Transactional
    public void initializeDefaultRoles() {
        ensureRoleExists("ROLE_ADMIN", "系统管理员", "拥有系统所有权限");
        ensureRoleExists("ROLE_USER", "普通用户", "基础访问权限");
        ensureRoleExists("ROLE_OPERATOR", "业务运营", "负责智能体业务配置、知识资产管理与工作流编排运营");
        ensureRoleExists("ROLE_PLATFORM_ADMIN", "平台管理员", "负责平台运行配置、监控治理与系统问题排查");
        ensureRoleExists("ROLE_SUPER_ADMIN", "超级管理员", "拥有全局控制权限，可管理组织与平台全部能力");
    }

    private void ensureRoleExists(String roleCode, String roleName, String description) {
        if (roleRepository.existsByRoleCode(roleCode)) {
            return;
        }

        SysRole role = SysRole.builder()
                .roleCode(roleCode)
                .roleName(roleName)
                .description(description)
                .build();
        roleRepository.save(role);
        log.info("Created default role: {}", roleCode);
    }
}
