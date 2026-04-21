package com.adlin.orin.modules.system.controller;

import com.adlin.orin.modules.audit.service.AuditHelper;
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
    private final AuditHelper auditHelper;

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

        auditHelper.log("SYSTEM", "MAIL_CONFIG_SAVE", "/api/v1/system/mail-config",
                "保存邮件配置: mailerType=" + saved.getMailerType() + ", fromEmail=" + saved.getFromEmail(),
                true, null);

        return ResponseEntity.ok(saved);
    }

    /**
     * 测试邮件配置
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testConfig(
            @RequestBody(required = false) Map<String, Object> payload,
            @RequestParam(value = "testEmail", required = false) String legacyTestEmail,
            @RequestParam(value = "to", required = false) String legacyToEmail) {
        String testEmail = resolveTestEmail(payload, legacyTestEmail, legacyToEmail);
        if (testEmail == null || testEmail.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "testEmail 不能为空"
            ));
        }

        MailConfigEntity config = mailConfigService.getConfig().orElse(null);
        if (config == null) {
            auditHelper.log("SYSTEM", "MAIL_CONFIG_TEST", "/api/v1/system/mail-config/test",
                    "测试邮件配置失败: 未配置邮件服务", false, "请先配置邮件服务");
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "请先配置邮件服务"
            ));
        }

        boolean success = mailConfigService.testConfig(config, testEmail);
        if (success) {
            auditHelper.log("SYSTEM", "MAIL_CONFIG_TEST", "/api/v1/system/mail-config/test",
                    "测试邮件发送成功: 收件人=" + testEmail, true, null);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "测试邮件发送成功"
            ));
        } else {
            auditHelper.log("SYSTEM", "MAIL_CONFIG_TEST", "/api/v1/system/mail-config/test",
                    "测试邮件发送失败: 收件人=" + testEmail, false, "邮件发送失败");
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "测试邮件发送失败，请检查配置"
            ));
        }
    }

    private String resolveTestEmail(Map<String, Object> payload, String legacyTestEmail, String legacyToEmail) {
        if (payload != null) {
            Object testEmail = payload.get("testEmail");
            if (testEmail instanceof String str && !str.isBlank()) {
                return str;
            }
            Object to = payload.get("to");
            if (to instanceof String str && !str.isBlank()) {
                return str;
            }
        }
        if (legacyTestEmail != null && !legacyTestEmail.isBlank()) {
            return legacyTestEmail;
        }
        if (legacyToEmail != null && !legacyToEmail.isBlank()) {
            return legacyToEmail;
        }
        return null;
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
    public ResponseEntity<?> sendMail(@RequestBody Map<String, Object> request) {
        String to = asString(request.get("to"));
        String cc = asString(request.get("cc"));
        String bcc = asString(request.get("bcc"));
        String subject = asString(request.get("subject"));
        String content = asString(request.get("content"));
        boolean separateSend = Boolean.parseBoolean(asString(request.get("separateSend")));

        if (to == null || to.isEmpty() || subject == null || subject.isEmpty() || content == null || content.isEmpty()) {
            auditHelper.log("SYSTEM", "MAIL_SEND", "/api/v1/system/mail-config/send",
                    "发送邮件失败: 参数不完整", false, "收件人、主题和内容不能为空");
            return ResponseEntity.badRequest().body("收件人、主题和内容不能为空");
        }

        MailConfigEntity config = mailConfigService.getConfig().orElse(null);
        if (config == null) {
            auditHelper.log("SYSTEM", "MAIL_SEND", "/api/v1/system/mail-config/send",
                    "发送邮件失败: 未配置邮件服务", false, "请先配置邮件服务");
            return ResponseEntity.badRequest().body("请先配置邮件服务");
        }

        boolean success = mailConfigService.sendMail(config, to, cc, bcc, subject, content, separateSend);
        if (success) {
            auditHelper.log("SYSTEM", "MAIL_SEND", "/api/v1/system/mail-config/send",
                    "发送邮件成功: 收件人=" + to + ", 抄送=" + cc + ", 密送=" + bcc + ", 主题=" + subject, true, null);
            return ResponseEntity.ok().body(Map.of("success", true, "message", "邮件发送成功"));
        } else {
            auditHelper.log("SYSTEM", "MAIL_SEND", "/api/v1/system/mail-config/send",
                    "发送邮件失败: 收件人=" + to + ", 抄送=" + cc + ", 密送=" + bcc + ", 主题=" + subject, false, "邮件发送失败");
            return ResponseEntity.badRequest().body("邮件发送失败，请检查配置");
        }
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
