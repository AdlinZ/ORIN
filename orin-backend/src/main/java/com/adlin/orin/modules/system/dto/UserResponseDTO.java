package com.adlin.orin.modules.system.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {

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

    @JsonProperty("id")
    private Long id;

    public static UserResponseDTO fromEntity(com.adlin.orin.modules.system.entity.SysUser user) {
        UserResponseDTO dto = new UserResponseDTO();
        dto.setUserId(user.getUserId());
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setEmail(user.getEmail());
        dto.setAvatar(user.getAvatar());
        dto.setBio(user.getBio());
        dto.setAddress(user.getAddress());
        dto.setPhone(user.getPhone());
        dto.setStatus(user.getStatus());
        dto.setRole(user.getRole());
        dto.setDepartmentId(user.getDepartmentId());
        dto.setCreateTime(user.getCreateTime());
        dto.setLastLoginTime(user.getLastLoginTime());
        dto.setId(user.getUserId());
        return dto;
    }
}
