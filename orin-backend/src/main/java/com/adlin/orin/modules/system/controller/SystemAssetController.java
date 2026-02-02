package com.adlin.orin.modules.system.controller;

import com.adlin.orin.common.service.FileStorageService;
import com.adlin.orin.modules.system.entity.SysUser;
import com.adlin.orin.modules.system.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemAssetController {

    private final FileStorageService fileStorageService;
    private final SysUserRepository userRepository;

    /**
     * Upload User Avatar
     * Saves file to storage/uploads/avatars and returns the relative path
     */
    @PostMapping("/upload/avatar")
    public ResponseEntity<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", required = false) Long userId) {
        try {
            String path = fileStorageService.storeFile(file, "avatars");

            // If userId provided, update user record
            if (userId != null) {
                userRepository.findById(userId).ifPresent((SysUser user) -> {
                    user.setAvatar(path);
                    userRepository.save(user);
                });
            }

            Map<String, String> response = new HashMap<>();
            response.put("path", path);
            response.put("url", "/uploads/" + path); // Assuming static resource mapping

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to upload avatar"));
        }
    }
}
