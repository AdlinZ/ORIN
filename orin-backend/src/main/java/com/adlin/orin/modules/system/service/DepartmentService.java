package com.adlin.orin.modules.system.service;

import com.adlin.orin.modules.system.entity.SysDepartment;
import com.adlin.orin.modules.system.repository.SysDepartmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 部门管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final SysDepartmentRepository departmentRepository;

    /**
     * 获取所有部门（扁平列表）
     */
    public List<SysDepartment> getAllDepartments() {
        log.info("DepartmentService: Getting all departments");
        List<SysDepartment> result = departmentRepository.findAll();
        log.info("DepartmentService: Found {} departments", result.size());
        return result;
    }

    /**
     * 获取部门树形列表
     */
    public List<SysDepartment> getDepartmentTree() {
        log.info("DepartmentService: Getting department tree");
        List<SysDepartment> allDepartments = departmentRepository.findAll();
        log.info("DepartmentService: Found {} departments for tree", allDepartments.size());
        return buildTree(allDepartments, 0L);
    }

    /**
     * 构建树形结构
     */
    private List<SysDepartment> buildTree(List<SysDepartment> allDepartments, Long parentId) {
        List<SysDepartment> tree = new ArrayList<>();
        for (SysDepartment dept : allDepartments) {
            Long deptParentId = dept.getParentId();
            if (deptParentId == null) {
                deptParentId = 0L;
            }
            if (parentId.equals(deptParentId)) {
                tree.add(dept);
            }
        }
        return tree;
    }

    /**
     * 根据ID获取部门
     */
    public Optional<SysDepartment> getDepartmentById(Long id) {
        return departmentRepository.findById(id);
    }

    /**
     * 创建部门
     */
    @Transactional
    public SysDepartment createDepartment(SysDepartment department) {
        // 检查部门编码是否已存在
        if (department.getDepartmentCode() != null &&
            departmentRepository.existsByDepartmentCode(department.getDepartmentCode())) {
            throw new RuntimeException("部门编码已存在: " + department.getDepartmentCode());
        }

        // 设置父部门
        if (department.getParentId() == null) {
            department.setParentId(0L);
        }

        // 设置排序号
        if (department.getOrderNum() == null) {
            department.setOrderNum(0);
        }

        return departmentRepository.save(department);
    }

    /**
     * 更新部门
     */
    @Transactional
    public SysDepartment updateDepartment(Long id, SysDepartment departmentDetails) {
        SysDepartment department = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("部门不存在"));

        // 检查编码冲突
        if (departmentDetails.getDepartmentCode() != null &&
            !departmentDetails.getDepartmentCode().equals(department.getDepartmentCode()) &&
            departmentRepository.existsByDepartmentCode(departmentDetails.getDepartmentCode())) {
            throw new RuntimeException("部门编码已存在: " + departmentDetails.getDepartmentCode());
        }

        if (departmentDetails.getDepartmentName() != null) {
            department.setDepartmentName(departmentDetails.getDepartmentName());
        }
        if (departmentDetails.getDepartmentCode() != null) {
            department.setDepartmentCode(departmentDetails.getDepartmentCode());
        }
        if (departmentDetails.getParentId() != null) {
            department.setParentId(departmentDetails.getParentId());
        }
        if (departmentDetails.getOrderNum() != null) {
            department.setOrderNum(departmentDetails.getOrderNum());
        }
        if (departmentDetails.getStatus() != null) {
            department.setStatus(departmentDetails.getStatus());
        }
        if (departmentDetails.getLeader() != null) {
            department.setLeader(departmentDetails.getLeader());
        }
        if (departmentDetails.getPhone() != null) {
            department.setPhone(departmentDetails.getPhone());
        }
        if (departmentDetails.getDescription() != null) {
            department.setDescription(departmentDetails.getDescription());
        }

        return departmentRepository.save(department);
    }

    /**
     * 删除部门
     */
    @Transactional
    public void deleteDepartment(Long id) {
        if (!departmentRepository.existsById(id)) {
            throw new RuntimeException("部门不存在");
        }

        // 检查是否有子部门
        long childCount = departmentRepository.countByParentId(id);
        if (childCount > 0) {
            throw new RuntimeException("该部门下存在子部门，无法删除");
        }

        departmentRepository.deleteById(id);
    }

    /**
     * 获取子部门列表
     */
    public List<SysDepartment> getChildDepartments(Long parentId) {
        return departmentRepository.findByParentIdOrderByOrderNumAsc(parentId);
    }

    /**
     * 初始化默认部门
     */
    @Transactional
    public void initializeDefaultDepartments() {
        if (!departmentRepository.existsByDepartmentCode("HQ")) {
            SysDepartment hq = SysDepartment.builder()
                    .departmentCode("HQ")
                    .departmentName("总部")
                    .parentId(0L)
                    .orderNum(0)
                    .status("ENABLED")
                    .description("公司总部")
                    .build();
            departmentRepository.save(hq);
            log.info("Created default department: HQ");
        }
    }
}