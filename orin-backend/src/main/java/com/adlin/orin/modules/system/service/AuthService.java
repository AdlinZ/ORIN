package com.adlin.orin.modules.system.service;

import com.adlin.orin.modules.system.entity.SysUser;
import com.adlin.orin.modules.system.repository.SysUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AuthService {

    @Autowired
    private SysUserRepository userRepository;

    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 用户登录
     * 
     * @return Map包含user和roles
     */
    public Map<String, Object> login(String username, String password) {
        Optional<SysUser> userOpt = userRepository.findByUsername(username);
        if (userOpt.isPresent()) {
            SysUser user = userOpt.get();

            if (passwordEncoder.matches(password, user.getPassword())) {
                // 加载用户角色
                List<String> roles = userRoleService.getUserRoleCodes(user.getUserId());

                Map<String, Object> result = new HashMap<>();
                result.put("user", user);
                result.put("roles", roles);
                return result;
            }
        }
        return null;
    }
}
