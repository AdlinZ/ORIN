package com.adlin.orin.modules.system.controller;

import com.adlin.orin.modules.system.entity.SysUser;
import com.adlin.orin.modules.system.repository.SysUserRepository;
import com.adlin.orin.modules.system.service.MailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 用户验证控制器 - 邮箱/手机绑定验证码
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "User Verification", description = "用户验证与绑定")
public class UserVerificationController {

    private final MailService mailService;
    private final SysUserRepository userRepository;

    @Operation(summary = "发送邮箱验证码")
    @PostMapping("/send-code")
    public Map<String, Object> sendVerificationCode(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String type = request.getOrDefault("type", "bind"); // bind, resetPassword
        
        Map<String, Object> response = new HashMap<>();
        
        if (email == null || email.isEmpty()) {
            response.put("success", false);
            response.put("message", "邮箱不能为空");
            return response;
        }
        
        // 检查邮箱格式
        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            response.put("success", false);
            response.put("message", "邮箱格式不正确");
            return response;
        }
        
        // 如果是绑定类型，检查邮箱是否已被使用
        if ("bind".equals(type)) {
            Optional<SysUser> existing = userRepository.findByEmail(email);
            if (existing.isPresent()) {
                response.put("success", false);
                response.put("message", "该邮箱已被绑定");
                return response;
            }
        }
        
        boolean sent = mailService.sendVerificationCode(email, type);
        response.put("success", sent);
        response.put("message", sent ? "验证码已发送" : "发送失败，请稍后重试");
        
        return response;
    }

    @Operation(summary = "验证邮箱验证码")
    @PostMapping("/verify-code")
    public Map<String, Object> verifyCode(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String code = request.get("code");
        
        Map<String, Object> response = new HashMap<>();
        
        if (email == null || code == null) {
            response.put("success", false);
            response.put("message", "参数不完整");
            return response;
        }
        
        boolean valid = mailService.verifyCode(email, code);
        response.put("success", valid);
        response.put("message", valid ? "验证成功" : "验证码错误或已过期");
        
        return response;
    }

    @Operation(summary = "绑定邮箱")
    @PostMapping("/bind-email")
    public Map<String, Object> bindEmail(@RequestBody Map<String, String> request) {
        String userId = request.get("userId");
        String email = request.get("email");
        String code = request.get("code");
        
        Map<String, Object> response = new HashMap<>();
        
        // 验证验证码
        if (!mailService.verifyCode(email, code)) {
            response.put("success", false);
            response.put("message", "验证码错误或已过期");
            return response;
        }
        
        // 更新用户邮箱
        Optional<SysUser> userOpt = userRepository.findById(Long.parseLong(userId));
        if (userOpt.isEmpty()) {
            response.put("success", false);
            response.put("message", "用户不存在");
            return response;
        }
        
        SysUser user = userOpt.get();
        user.setEmail(email);
        userRepository.save(user);
        
        response.put("success", true);
        response.put("message", "邮箱绑定成功");
        
        return response;
    }

    @Operation(summary = "测试邮件服务")
    @GetMapping("/mail-test")
    public Map<String, Object> testMail(@RequestParam String to) {
        Map<String, Object> response = new HashMap<>();
        
        boolean sent = mailService.sendAlertEmail(to, "测试邮件", "这是一封测试邮件，邮件服务配置正常。");
        response.put("success", sent);
        response.put("message", sent ? "测试邮件发送成功" : "发送失败，请检查配置");
        
        return response;
    }
}
