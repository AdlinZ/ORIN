package com.adlin.orin.modules.system.repository;

import com.adlin.orin.modules.system.entity.SysUserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户角色关联数据访问接口
 */
@Repository
public interface SysUserRoleRepository extends JpaRepository<SysUserRole, Long> {

    /**
     * 查找用户的所有角色关联
     */
    List<SysUserRole> findByUserId(Long userId);

    /**
     * 查找角色的所有用户关联
     */
    List<SysUserRole> findByRoleId(Long roleId);

    /**
     * 删除用户的所有角色
     */
    void deleteByUserId(Long userId);

    /**
     * 删除用户的指定角色
     */
    void deleteByUserIdAndRoleId(Long userId, Long roleId);

    /**
     * 检查用户是否拥有指定角色
     */
    boolean existsByUserIdAndRoleId(Long userId, Long roleId);

    /**
     * 获取用户的角色代码列表
     */
    @Query("SELECT r.roleCode FROM SysRole r JOIN SysUserRole ur ON r.roleId = ur.roleId WHERE ur.userId = ?1")
    List<String> findRoleCodesByUserId(Long userId);
}
