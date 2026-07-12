package com.adlin.orin.modules.system.service.dto;

import com.adlin.orin.modules.system.entity.SysUser;

import java.util.List;

public record AuthResult(SysUser user, List<String> roles) {
}
