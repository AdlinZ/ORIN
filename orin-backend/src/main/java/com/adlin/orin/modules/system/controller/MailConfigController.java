package com.adlin.orin.modules.system.controller;

import com.adlin.orin.modules.system.entity.MailConfigEntity;
import com.adlin.orin.modules.system.service.MailConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 邮件配置管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/system/mail-config")
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
        log.info("接收到邮件配置保存请求: mailerType={}, fromEmail={}", config.getMailerType(), config.getFromEmail());
        MailConfigEntity saved = mailConfigService.saveConfig(config);
        log.info("邮件配置保存完成: mailerType={}", saved.getMailerType());
        return ResponseEntity.ok(saved);
    }

    /**
     * 测试邮件配置
     */
    @PostMapping("/test")
    public ResponseEntity<?> testConfig(@RequestParam String testEmail) {
        MailConfigEntity config = mailConfigService.getConfig().orElse(null);
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

    /**
     * 发送邮件
     */
    @PostMapping("/send")
    public ResponseEntity<?> sendMail(@RequestBody Map<String, String> request) {
        String to = request.get("to");
        String subject = request.get("subject");
        String content = request.get("content");

        if (to == null || to.isEmpty() || subject == null || subject.isEmpty() || content == null || content.isEmpty()) {
            return ResponseEntity.badRequest().body("收件人、主题和内容不能为空");
        }

        MailConfigEntity config = mailConfigService.getConfig().orElse(null);
        if (config == null) {
            return ResponseEntity.badRequest().body("请先配置邮件服务");
        }

        boolean success = mailConfigService.sendMail(config, to, subject, content);
        if (success) {
            return ResponseEntity.ok().body(Map.of("success", true, "message", "邮件发送成功"));
        } else {
            return ResponseEntity.badRequest().body("邮件发送失败，请检查配置");
        }
    }
}