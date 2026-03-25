package com.adlin.orin.modules.system.controller;

import com.adlin.orin.modules.audit.service.AuditHelper;
import com.adlin.orin.modules.system.entity.SysUser;
import com.adlin.orin.modules.system.repository.SysUserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 用户管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "用户管理")
public class UserManageController {

    private final SysUserRepository userRepository;
    private final AuditHelper auditHelper;

    @Operation(summary = "获取用户列表")
    @GetMapping
    public Map<String, Object> getUserList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<SysUser> userPage = userRepository.findAll(pageRequest);

        List<SysUser> users = userPage.getContent();

        // 如果有搜索条件，过滤结果
        if (search != null && !search.isEmpty()) {
            users = users.stream()
                    .filter(u -> u.getUsername().contains(search) ||
                               (u.getEmail() != null && u.getEmail().contains(search)))
                    .toList();
        }

        if (role != null && !role.isEmpty()) {
            users = users.stream()
                    .filter(u -> role.equals(u.getRole()))
                    .toList();
        }

        // 使用 DTO
        List<Map<String, Object>> userResponses = users.stream()
                .map(u -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("userId", u.getUserId());
                    map.put("username", u.getUsername());
                    map.put("nickname", u.getNickname());
                    map.put("email", u.getEmail());
                    map.put("avatar", u.getAvatar());
                    map.put("bio", u.getBio());
                    map.put("address", u.getAddress());
                    map.put("phone", u.getPhone());
                    map.put("status", u.getStatus());
                    map.put("role", u.getRole());
                    // 强制添加 departmentId 字段，添加日志
                    Long deptId = u.getDepartmentId();
                    log.info("User {} has departmentId: {}", u.getUsername(), deptId);
                    map.put("departmentId", deptId);
                    map.put("createTime", u.getCreateTime());
                    map.put("lastLoginTime", u.getLastLoginTime());
                    map.put("id", u.getUserId());
                    return map;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("data", userResponses);
        result.put("total", userPage.getTotalElements());
        result.put("page", page);
        result.put("size", size);

        return result;
    }

    @Operation(summary = "获取用户详情")
    @GetMapping("/{id}")
    public Map<String, Object> getUser(@PathVariable Long id) {
        Optional<SysUser> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return Map.of("error", "User not found");
        }
        SysUser user = userOpt.get();
        Map<String, Object> result = new HashMap<>();
        result.put("userId", user.getUserId());
        result.put("username", user.getUsername());
        result.put("nickname", user.getNickname());
        result.put("email", user.getEmail());
        result.put("avatar", user.getAvatar());
        result.put("bio", user.getBio());
        result.put("address", user.getAddress());
        result.put("phone", user.getPhone());
        result.put("status", user.getStatus());
        result.put("role", user.getRole());
        result.put("departmentId", user.getDepartmentId());
        result.put("createTime", user.getCreateTime());
        result.put("lastLoginTime", user.getLastLoginTime());
        result.put("id", user.getUserId());
        return result;
    }

    @Operation(summary = "创建用户")
    @PostMapping
    public Map<String, Object> createUser(@RequestBody SysUser user) {
        // 检查用户名是否存在
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            auditHelper.log("SYSTEM", "USER_CREATE", "/api/v1/users",
                    "创建用户失败: 用户名 " + user.getUsername() + " 已存在", false, "用户名已存在");
            return Map.of("success", false, "message", "用户名已存在");
        }

        // 检查邮箱是否已被使用
        if (user.getEmail() != null && !user.getEmail().isEmpty()) {
            Optional<SysUser> existingByEmail = userRepository.findByEmail(user.getEmail());
            if (existingByEmail.isPresent()) {
                auditHelper.log("SYSTEM", "USER_CREATE", "/api/v1/users",
                        "创建用户失败: 邮箱 " + user.getEmail() + " 已被使用", false, "邮箱已被使用");
                return Map.of("success", false, "message", "邮箱已被使用");
            }
        }

        // 设置默认角色
        if (user.getRole() == null || user.getRole().isEmpty()) {
            user.setRole("ROLE_USER");
        }

        // 注意：不设置默认部门，让 departmentId 为 null

        SysUser saved = userRepository.save(user);

        auditHelper.log("SYSTEM", "USER_CREATE", "/api/v1/users",
                "创建用户成功: " + saved.getUsername() + ", 角色: " + saved.getRole(), true, null);

        Map<String, Object> userData = new HashMap<>();
        userData.put("userId", saved.getUserId());
        userData.put("username", saved.getUsername());
        userData.put("nickname", saved.getNickname());
        userData.put("email", saved.getEmail());
        userData.put("avatar", saved.getAvatar());
        userData.put("bio", saved.getBio());
        userData.put("address", saved.getAddress());
        userData.put("phone", saved.getPhone());
        userData.put("status", saved.getStatus());
        userData.put("role", saved.getRole());
        userData.put("departmentId", saved.getDepartmentId());
        userData.put("createTime", saved.getCreateTime());
        userData.put("lastLoginTime", saved.getLastLoginTime());
        userData.put("id", saved.getUserId());

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", userData);
        result.put("message", "用户创建成功");

        return result;
    }

    @Operation(summary = "更新用户")
    @PutMapping("/{id}")
    public Map<String, Object> updateUser(@PathVariable Long id, @RequestBody SysUser userDetails) {
        Optional<SysUser> userOpt = userRepository.findById(id);

        if (userOpt.isEmpty()) {
            auditHelper.log("SYSTEM", "USER_UPDATE", "/api/v1/users/" + id,
                    "更新用户失败: 用户ID " + id + " 不存在", false, "用户不存在");
            return Map.of("success", false, "message", "用户不存在");
        }

        SysUser user = userOpt.get();
        String oldInfo = "email=" + user.getEmail() + ", role=" + user.getRole();

        // 更新可编辑字段
        if (userDetails.getEmail() != null) {
            user.setEmail(userDetails.getEmail());
        }
        if (userDetails.getRole() != null) {
            user.setRole(userDetails.getRole());
        }
        if (userDetails.getStatus() != null) {
            user.setStatus(userDetails.getStatus());
        }
        // 允许设置 departmentId 为 null 或 0 表示无部门
        if (userDetails.getDepartmentId() != null && userDetails.getDepartmentId() != 0L) {
            user.setDepartmentId(userDetails.getDepartmentId());
        } else {
            user.setDepartmentId(null);
        }

        userRepository.save(user);

        auditHelper.log("SYSTEM", "USER_UPDATE", "/api/v1/users/" + id,
                "更新用户成功: " + user.getUsername() + ", 更新内容: " + oldInfo, true, null);

        return Map.of("success", true, "message", "用户更新成功");
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteUser(@PathVariable Long id) {
        Optional<SysUser> userOpt = userRepository.findById(id);
        if (!userRepository.existsById(id)) {
            auditHelper.log("SYSTEM", "USER_DELETE", "/api/v1/users/" + id,
                    "删除用户失败: 用户ID " + id + " 不存在", false, "用户不存在");
            return Map.of("success", false, "message", "用户不存在");
        }

        String username = userOpt.map(SysUser::getUsername).orElse("unknown");
        userRepository.deleteById(id);

        auditHelper.log("SYSTEM", "USER_DELETE", "/api/v1/users/" + id,
                "删除用户成功: " + username, true, null);

        return Map.of("success", true, "message", "用户删除成功");
    }

    @Operation(summary = "启用/禁用用户")
    @PutMapping("/{id}/status")
    public Map<String, Object> toggleUserStatus(@PathVariable Long id, @RequestBody Map<String, Boolean> payload) {
        Boolean enabled = payload.get("enabled");

        Optional<SysUser> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            auditHelper.log("SYSTEM", "USER_STATUS", "/api/v1/users/" + id + "/status",
                    "修改用户状态失败: 用户ID " + id + " 不存在", false, "用户不存在");
            return Map.of("success", false, "message", "用户不存在");
        }

        SysUser user = userOpt.get();
        user.setStatus(enabled ? "active" : "disabled");
        userRepository.save(user);

        auditHelper.log("SYSTEM", "USER_STATUS", "/api/v1/users/" + id + "/status",
                "修改用户状态成功: " + user.getUsername() + ", 状态: " + (enabled ? "启用" : "禁用"), true, null);

        return Map.of("success", true, "message", enabled ? "用户已启用" : "用户已禁用");
    }

    @Operation(summary = "获取角色列表")
    @GetMapping("/roles")
    public List<Map<String, String>> getRoles() {
        return List.of(
            Map.of("id", "ROLE_ADMIN", "name", "管理员"),
            Map.of("id", "ROLE_USER", "name", "普通用户"),
            Map.of("id", "ROLE_GUEST", "name", "访客")
        );
    }
}
