package com.adlin.orin.modules.system.repository;

import com.adlin.orin.modules.system.entity.SysRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 角色数据访问接口
 */
@Repository
public interface SysRoleRepository extends JpaRepository<SysRole, Long> {

    /**
     * 根据角色代码查找角色
     */
    Optional<SysRole> findByRoleCode(String roleCode);

    /**
     * 检查角色代码是否存在
     */
    boolean existsByRoleCode(String roleCode);
}
