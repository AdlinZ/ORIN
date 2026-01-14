package com.adlin.orin.modules.system.repository;

import com.adlin.orin.modules.system.entity.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {
    Optional<SysUser> findByUsername(String username);
}
