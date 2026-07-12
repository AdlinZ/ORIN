package com.adlin.orin.modules.system.dto;

import lombok.Data;

@Data
public class RegisterDTO {
    private String username;
    private String password;
    private String nickname;
    private String email;
    private boolean rememberMe;
}
