package com.adlin.orin.modules.system.repository;

import com.adlin.orin.modules.system.entity.SysDepartment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 部门数据访问接口
 */
@Repository
public interface SysDepartmentRepository extends JpaRepository<SysDepartment, Long> {

    /**
     * 根据父部门ID获取子部门列表
     */
    List<SysDepartment> findByParentIdOrderByOrderNumAsc(Long parentId);

    /**
     * 根据部门编码查找部门
     */
    SysDepartment findByDepartmentCode(String departmentCode);

    /**
     * 检查部门编码是否存在
     */
    boolean existsByDepartmentCode(String departmentCode);

    /**
     * 根据部门名称模糊查询
     */
    List<SysDepartment> findByDepartmentNameContaining(String departmentName);

    /**
     * 获取所有顶级部门（父ID为null或0）
     */
    @Query("SELECT d FROM SysDepartment d WHERE d.parentId IS NULL OR d.parentId = 0 ORDER BY d.orderNum ASC")
    List<SysDepartment> findTopLevelDepartments();

    /**
     * 统计子部门数量
     */
    long countByParentId(Long parentId);
}