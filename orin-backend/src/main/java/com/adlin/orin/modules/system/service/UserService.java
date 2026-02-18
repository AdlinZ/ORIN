package com.adlin.orin.modules.system.service;

import com.adlin.orin.modules.system.entity.SysUser;
import com.adlin.orin.modules.system.repository.SysUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private SysUserRepository userRepository;

    public Optional<SysUser> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<SysUser> getUserById(Long userId) {
        return userRepository.findById(userId);
    }

    @Transactional
    public SysUser updateUser(SysUser user) {
        return userRepository.save(user);
    }

    @Transactional
    public void updateAvatar(Long userId, String avatarUrl) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setAvatar(avatarUrl);
            userRepository.save(user);
        });
    }
}
