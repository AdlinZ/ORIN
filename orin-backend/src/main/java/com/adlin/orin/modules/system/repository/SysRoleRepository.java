package com.adlin.orin.modules.system.repository;

import com.adlin.orin.modules.system.entity.SysRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("""
            select role from SysRole role
            where (:search is null
                or lower(role.roleName) like lower(concat('%', :search, '%'))
                or lower(role.roleCode) like lower(concat('%', :search, '%'))
                or lower(role.description) like lower(concat('%', :search, '%')))
            """)
    Page<SysRole> findAllFiltered(@Param("search") String search, Pageable pageable);
}
