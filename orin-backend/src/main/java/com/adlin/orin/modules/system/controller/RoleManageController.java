package com.adlin.orin.modules.system.controller;

import com.adlin.orin.modules.system.service.RoleService;
import com.adlin.orin.modules.audit.service.AuditHelper;
import com.adlin.orin.modules.system.entity.SysRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "Role Management", description = "角色管理")
public class RoleManageController {

    private final RoleService roleService;
    private final AuditHelper auditHelper;

    @Operation(summary = "获取角色列表")
    @GetMapping
    public Map<String, Object> getRoleList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "roleId"));
        Page<SysRole> rolePage = roleService.getAllRolesPageable(pageRequest);

        List<SysRole> roles = rolePage.getContent();

        // 如果有搜索条件，过滤结果
        if (search != null && !search.isEmpty()) {
            roles = roles.stream()
                    .filter(r -> r.getRoleName().contains(search) ||
                            (r.getRoleCode() != null && r.getRoleCode().contains(search)) ||
                            (r.getDescription() != null && r.getDescription().contains(search)))
                    .toList();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("data", roles);
        result.put("total", rolePage.getTotalElements());
        result.put("page", page);
        result.put("size", size);

        return result;
    }

    @Operation(summary = "获取角色详情")
    @GetMapping("/{id}")
    public ResponseEntity<SysRole> getRole(@PathVariable Long id) {
        return roleService.getRoleById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "创建角色")
    @PostMapping
    public Map<String, Object> createRole(@RequestBody SysRole role) {
        try {
            // 检查角色代码格式
            if (role.getRoleCode() != null && !role.getRoleCode().startsWith("ROLE_")) {
                role.setRoleCode("ROLE_" + role.getRoleCode().toUpperCase());
            }

            SysRole saved = roleService.createRole(role);

            auditHelper.log("SYSTEM", "ROLE_CREATE", "/api/v1/roles",
                    "创建角色成功: " + saved.getRoleName() + ", 代码: " + saved.getRoleCode(), true, null);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", saved);
            result.put("message", "角色创建成功");

            return result;
        } catch (Exception e) {
            auditHelper.log("SYSTEM", "ROLE_CREATE", "/api/v1/roles",
                    "创建角色失败: " + e.getMessage(), false, e.getMessage());

            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @Operation(summary = "更新角色")
    @PutMapping("/{id}")
    public Map<String, Object> updateRole(@PathVariable Long id, @RequestBody SysRole roleDetails) {
        try {
            SysRole updated = roleService.updateRole(id, roleDetails);

            auditHelper.log("SYSTEM", "ROLE_UPDATE", "/api/v1/roles/" + id,
                    "更新角色成功: " + updated.getRoleName(), true, null);

            return Map.of("success", true, "message", "角色更新成功", "data", updated);
        } catch (Exception e) {
            auditHelper.log("SYSTEM", "ROLE_UPDATE", "/api/v1/roles/" + id,
                    "更新角色失败: " + e.getMessage(), false, e.getMessage());

            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteRole(@PathVariable Long id) {
        try {
            roleService.deleteRole(id);

            auditHelper.log("SYSTEM", "ROLE_DELETE", "/api/v1/roles/" + id,
                    "删除角色成功: ID " + id, true, null);

            return Map.of("success", true, "message", "角色删除成功");
        } catch (Exception e) {
            auditHelper.log("SYSTEM", "ROLE_DELETE", "/api/v1/roles/" + id,
                    "删除角色失败: " + e.getMessage(), false, e.getMessage());

            return Map.of("success", false, "message", e.getMessage());
        }
    }
}