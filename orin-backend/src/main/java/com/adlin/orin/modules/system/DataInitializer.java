package com.adlin.orin.modules.system;

import com.adlin.orin.modules.system.entity.SysUser;
import com.adlin.orin.modules.system.entity.SysRole;
import com.adlin.orin.modules.system.entity.SysUserRole;
import com.adlin.orin.modules.system.repository.SysRoleRepository;
import com.adlin.orin.modules.system.repository.SysUserRepository;
import com.adlin.orin.modules.system.repository.SysUserRoleRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final SysUserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final String defaultAdminPassword;

    public DataInitializer(SysUserRepository userRepository,
                           SysRoleRepository roleRepository,
                           SysUserRoleRepository userRoleRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${orin.default-admin.password:}") String defaultAdminPassword) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.defaultAdminPassword = defaultAdminPassword;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        SysRole adminRole = findOrCreateRole("ROLE_ADMIN", "系统管理员", "拥有系统所有权限,可管理用户、配置、API等");
        SysRole superAdminRole = findOrCreateRole("ROLE_SUPER_ADMIN", "超级管理员", "拥有全局控制权限，可管理组织与平台全部能力");

        SysUser admin = userRepository.findByUsername("admin").orElse(null);
        if (admin == null) {
            if (!hasDefaultAdminPassword()) {
                log.warn("Default admin user was not created because ORIN_DEFAULT_ADMIN_PASSWORD is not configured.");
                return;
            }
            admin = new SysUser();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode(defaultAdminPassword));
            admin.setNickname("Administrator");
            admin.setEmail("admin@orin.com");
            admin.setStatus("ENABLED");
            admin.setRole("ROLE_SUPER_ADMIN");
            admin = userRepository.save(admin);
            log.info("Default admin user created.");
        } else {
            if (hasDefaultAdminPassword() && defaultAdminPassword.equals(admin.getPassword())) {
                admin.setPassword(passwordEncoder.encode(defaultAdminPassword));
            }
            admin.setRole("ROLE_SUPER_ADMIN");
            admin = userRepository.save(admin);
        }

        ensureUserRole(admin, adminRole);
        ensureUserRole(admin, superAdminRole);
    }

    private SysRole findOrCreateRole(String roleCode, String roleName, String description) {
        return roleRepository.findByRoleCode(roleCode).orElseGet(() -> roleRepository.save(SysRole.builder()
                .roleCode(roleCode)
                .roleName(roleName)
                .description(description)
                .build()));
    }

    private void ensureUserRole(SysUser user, SysRole role) {
        if (!userRoleRepository.existsByUserIdAndRoleId(user.getUserId(), role.getRoleId())) {
            userRoleRepository.save(SysUserRole.builder()
                    .userId(user.getUserId())
                    .roleId(role.getRoleId())
                    .build());
        }
    }

    private boolean hasDefaultAdminPassword() {
        return defaultAdminPassword != null && !defaultAdminPassword.isBlank();
    }
}
