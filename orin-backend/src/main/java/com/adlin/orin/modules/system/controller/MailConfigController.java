package com.adlin.orin.modules.system.controller;

import com.adlin.orin.modules.system.entity.MailConfigEntity;
import com.adlin.orin.modules.system.service.MailConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 邮件配置管理控制器
 */
@RestController
@RequestMapping("/api/system/mail-config")
@RequiredArgsConstructor
public class MailConfigController {

    private final MailConfigService mailConfigService;

    /**
     * 获取邮件配置
     */
    @GetMapping
    public ResponseEntity<MailConfigEntity> getConfig() {
        MailConfigEntity config = mailConfigService.getConfigForAdmin();
        return ResponseEntity.ok(config);
    }

    /**
     * 保存邮件配置
     */
    @PostMapping
    public ResponseEntity<MailConfigEntity> saveConfig(@RequestBody MailConfigEntity config) {
        MailConfigEntity saved = mailConfigService.saveConfig(config);
        return ResponseEntity.ok(saved);
    }

    /**
     * 测试邮件配置
     */
    @PostMapping("/test")
    public ResponseEntity<?> testConfig(@RequestParam String testEmail) {
        MailConfigEntity config = mailConfigService.getConfigForAdmin();
        if (config == null) {
            return ResponseEntity.badRequest().body("请先配置邮件服务");
        }

        boolean success = mailConfigService.testConfig(config, testEmail);
        if (success) {
            return ResponseEntity.ok().body("测试邮件发送成功");
        } else {
            return ResponseEntity.badRequest().body("测试邮件发送失败，请检查配置");
        }
    }

    /**
     * 获取配置状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        MailConfigEntity config = mailConfigService.getConfigForAdmin();
        Map<String, Object> status = new HashMap<>();

        boolean configured = config != null && config.getEnabled() != null && config.getEnabled();
        status.put("configured", configured);
        status.put("mailerType", config != null ? config.getMailerType() : null);
        status.put("fromEmail", config != null ? config.getFromEmail() : null);
        status.put("fromName", config != null ? config.getFromName() : null);

        return ResponseEntity.ok(status);
    }
}