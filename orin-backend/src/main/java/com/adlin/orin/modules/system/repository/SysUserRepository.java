package com.adlin.orin.modules.system.repository;

import com.adlin.orin.modules.system.entity.SysUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {
    Optional<SysUser> findByUsername(String username);
    Optional<SysUser> findByEmail(String email);

    @Query("""
            select user from SysUser user
            where (:search is null
                or lower(user.username) like lower(concat('%', :search, '%'))
                or lower(user.email) like lower(concat('%', :search, '%')))
              and (:role is null or user.role = :role)
              and (:departmentId is null or user.departmentId = :departmentId)
              and (
                :status is null
                or (:status = 'ENABLED' and upper(user.status) in ('ENABLED', 'ACTIVE'))
                or (:status = 'DISABLED' and upper(user.status) in ('DISABLED', 'INACTIVE'))
              )
            """)
    Page<SysUser> findAllFiltered(
            @Param("search") String search,
            @Param("role") String role,
            @Param("departmentId") Long departmentId,
            @Param("status") String status,
            Pageable pageable);
}
