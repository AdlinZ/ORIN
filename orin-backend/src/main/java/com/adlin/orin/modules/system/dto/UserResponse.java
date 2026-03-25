package com.adlin.orin.modules.system.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserResponse {
    @JsonProperty("userId")
    private Long userId;

    @JsonProperty("username")
    private String username;

    @JsonProperty("nickname")
    private String nickname;

    @JsonProperty("email")
    private String email;

    @JsonProperty("avatar")
    private String avatar;

    @JsonProperty("bio")
    private String bio;

    @JsonProperty("address")
    private String address;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("status")
    private String status;

    @JsonProperty("role")
    private String role;

    @JsonProperty("departmentId")
    private Long departmentId;

    @JsonProperty("createTime")
    private LocalDateTime createTime;

    @JsonProperty("lastLoginTime")
    private LocalDateTime lastLoginTime;

    public static UserResponse from(com.adlin.orin.modules.system.entity.SysUser user) {
        UserResponse response = new UserResponse();
        response.setUserId(user.getUserId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setEmail(user.getEmail());
        response.setAvatar(user.getAvatar());
        response.setBio(user.getBio());
        response.setAddress(user.getAddress());
        response.setPhone(user.getPhone());
        response.setStatus(user.getStatus());
        response.setRole(user.getRole());
        response.setDepartmentId(user.getDepartmentId());
        response.setCreateTime(user.getCreateTime());
        response.setLastLoginTime(user.getLastLoginTime());
        return response;
    }
}