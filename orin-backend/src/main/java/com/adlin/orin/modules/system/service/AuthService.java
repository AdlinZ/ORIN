package com.adlin.orin.modules.system.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.system.dto.RegisterDTO;
import com.adlin.orin.modules.system.entity.SysRole;
import com.adlin.orin.modules.system.entity.SysUser;
import com.adlin.orin.modules.system.entity.SysUserRole;
import com.adlin.orin.modules.system.repository.SysRoleRepository;
import com.adlin.orin.modules.system.repository.SysUserRepository;
import com.adlin.orin.modules.system.repository.SysUserRoleRepository;
import com.adlin.orin.modules.system.service.dto.AuthResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    private static final String DEFAULT_SELF_SERVICE_ROLE = "ROLE_USER";

    @Autowired
    private SysUserRepository userRepository;

    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private SysRoleRepository roleRepository;

    @Autowired
    private SysUserRoleRepository userRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${orin.auth.self-registration-enabled:true}")
    private boolean selfRegistrationEnabled;

    /**
     * 用户登录
     * 
     * @return Map包含user和roles
     */
    public AuthResult login(String username, String password) {
        Optional<SysUser> userOpt = userRepository.findByUsername(trimToNull(username));
        if (userOpt.isPresent()) {
            SysUser user = userOpt.get();

            if (passwordEncoder.matches(password, user.getPassword())) {
                if ("DISABLED".equalsIgnoreCase(user.getStatus()) || "disabled".equalsIgnoreCase(user.getStatus())) {
                    throw new BusinessException(ErrorCode.AUTH_INSUFFICIENT_PERMISSIONS, "账号已禁用");
                }
                // 更新最后登录时间
                user.setLastLoginTime(LocalDateTime.now());
                userRepository.save(user);

                // 加载用户角色
                List<String> roles = userRoleService.getUserRoleCodes(user.getUserId());
                if (roles.isEmpty() && user.getRole() != null && !user.getRole().isBlank()) {
                    roles = List.of(user.getRole());
                }

                return new AuthResult(user, roles);
            }
        }
        return null;
    }

    @Transactional
    public AuthResult register(RegisterDTO request) {
        if (!selfRegistrationEnabled) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前环境未开放自助注册");
        }

        String username = normalize(request != null ? request.getUsername() : null);
        String email = normalize(request != null ? request.getEmail() : null);
        validateRegistration(username, request != null ? request.getPassword() : null, email);

        if (userRepository.findByUsername(username).isPresent()) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "用户名已存在");
        }
        if (email != null && userRepository.findByEmail(email).isPresent()) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "邮箱已被使用");
        }

        SysRole userRole = roleRepository.findByRoleCode(DEFAULT_SELF_SERVICE_ROLE)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "默认用户角色不存在"));

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(blankToNull(request.getNickname()));
        user.setEmail(email);
        user.setStatus("ENABLED");
        user.setRole(DEFAULT_SELF_SERVICE_ROLE);

        SysUser saved = userRepository.save(user);
        userRoleRepository.save(SysUserRole.builder()
                .userId(saved.getUserId())
                .roleId(userRole.getRoleId())
                .build());

        return new AuthResult(saved, List.of(DEFAULT_SELF_SERVICE_ROLE));
    }

    public boolean isSelfRegistrationEnabled() {
        return selfRegistrationEnabled;
    }

    private void validateRegistration(String username, String password, String email) {
        if (username == null || username.length() < 3 || username.length() > 32) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "用户名长度需为 3-32 个字符");
        }
        if (!username.matches("^[a-zA-Z0-9_.-]+$")) {
            throw new BusinessException(ErrorCode.VALIDATION_INVALID_FORMAT, "用户名仅支持字母、数字、下划线、点和连字符");
        }
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "密码长度需为 8-128 个字符");
        }
        if (email != null && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new BusinessException(ErrorCode.VALIDATION_INVALID_FORMAT, "邮箱格式不正确");
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
