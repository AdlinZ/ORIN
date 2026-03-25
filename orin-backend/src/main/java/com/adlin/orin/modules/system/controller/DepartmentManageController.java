package com.adlin.orin.modules.system.controller;

import com.adlin.orin.modules.system.entity.SysDepartment;
import com.adlin.orin.modules.system.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 部门管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
@Tag(name = "Department Management", description = "部门管理")
public class DepartmentManageController {

    private final DepartmentService departmentService;

    @Operation(summary = "获取部门列表（树形结构）")
    @GetMapping
    public Map<String, Object> getDepartmentList() {
        try {
            log.info("Fetching department tree...");
            List<SysDepartment> departments = departmentService.getDepartmentTree();
            log.info("Found {} departments", departments.size());

            Map<String, Object> result = new HashMap<>();
            result.put("data", departments);
            result.put("total", departments.size());

            return result;
        } catch (Exception e) {
            log.error("Error fetching departments", e);
            return Map.of("data", List.of(), "total", 0, "error", e.getMessage());
        }
    }

    @Operation(summary = "获取所有部门（扁平列表）")
    @GetMapping("/all")
    public Map<String, Object> getAllDepartments() {
        try {
            log.info("Fetching all departments...");
            List<SysDepartment> departments = departmentService.getAllDepartments();
            log.info("Found {} departments", departments.size());

            Map<String, Object> result = new HashMap<>();
            result.put("data", departments);
            result.put("total", departments.size());

            return result;
        } catch (Exception e) {
            log.error("Error fetching all departments", e);
            return Map.of("data", List.of(), "total", 0, "error", e.getMessage());
        }
    }

    @Operation(summary = "获取部门详情")
    @GetMapping("/{id}")
    public ResponseEntity<SysDepartment> getDepartment(@PathVariable Long id) {
        return departmentService.getDepartmentById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "获取子部门列表")
    @GetMapping("/{id}/children")
    public Map<String, Object> getChildDepartments(@PathVariable Long id) {
        List<SysDepartment> children = departmentService.getChildDepartments(id);

        Map<String, Object> result = new HashMap<>();
        result.put("data", children);
        result.put("total", children.size());

        return result;
    }

    @Operation(summary = "创建部门")
    @PostMapping
    public Map<String, Object> createDepartment(@RequestBody SysDepartment department) {
        try {
            SysDepartment saved = departmentService.createDepartment(department);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", saved);
            result.put("message", "部门创建成功");

            return result;
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @Operation(summary = "更新部门")
    @PutMapping("/{id}")
    public Map<String, Object> updateDepartment(@PathVariable Long id, @RequestBody SysDepartment departmentDetails) {
        try {
            SysDepartment updated = departmentService.updateDepartment(id, departmentDetails);

            return Map.of("success", true, "message", "部门更新成功", "data", updated);
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @Operation(summary = "删除部门")
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteDepartment(@PathVariable Long id) {
        try {
            departmentService.deleteDepartment(id);

            return Map.of("success", true, "message", "部门删除成功");
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }
}