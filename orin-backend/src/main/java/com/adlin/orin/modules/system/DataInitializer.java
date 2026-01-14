package com.adlin.orin.modules.system;

import com.adlin.orin.modules.system.entity.SysUser;
import com.adlin.orin.modules.system.repository.SysUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final SysUserRepository userRepository;

    public DataInitializer(SysUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.findByUsername("admin").isEmpty()) {
            SysUser admin = new SysUser();
            admin.setUsername("admin");
            admin.setPassword("admin123"); // In real app, encode this
            admin.setNickname("Administrator");
            admin.setEmail("admin@orin.com");
            admin.setStatus("ENABLED");
            userRepository.save(admin);
            System.out.println("Default admin user created.");
        }
    }
}
