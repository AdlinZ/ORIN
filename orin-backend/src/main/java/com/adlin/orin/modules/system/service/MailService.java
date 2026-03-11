package com.adlin.orin.modules.system.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 邮件服务 - 发送验证码和系统通知
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${app.name:ORIN}")
    private String appName;

    // 验证码缓存 (email -> code)
    private final Map<String, VerificationCode> codeCache = new ConcurrentHashMap<>();
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    private static final int CODE_EXPIRE_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 5;

    /**
     * 发送验证码
     */
    public boolean sendVerificationCode(String email, String type) {
        // 检查发送次数
        VerificationCode existing = codeCache.get(email);
        if (existing != null && existing.getAttempts() >= MAX_ATTEMPTS) {
            log.warn("验证码发送次数过多: {}", email);
            return false;
        }

        String code = generateCode();
        
        String subject = "[%s] 邮箱验证码".formatted(appName);
        String content = "您好，您的验证码是：\n\n" +
                "【" + code + "】\n\n" +
                "有效期 " + CODE_EXPIRE_MINUTES + " 分钟，请尽快完成验证。\n\n" +
                "如果不是您本人操作，请忽略此邮件。";

        boolean success = sendEmail(email, subject, content);
        
        if (success) {
            codeCache.put(email, new VerificationCode(code, type));
            // 5分钟后过期
            scheduler.schedule(() -> codeCache.remove(email), CODE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        } else {
            // 记录失败次数
            if (existing != null) {
                existing.incrementAttempts();
            }
        }
        
        return success;
    }

    /**
     * 发送告警邮件
     */
    public boolean sendAlertEmail(String to, String subject, String content) {
        return sendEmail(to, "[" + appName + " 告警] " + subject, content);
    }

    /**
     * 发送告警通知（支持多个收件人）
     */
    public boolean sendAlertEmail(String[] toArr, String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toArr);
            message.setSubject("[" + appName + " 告警] " + subject);
            message.setText(content);
            message.setSentDate(new java.util.Date());
            
            mailSender.send(message);
            log.info("告警邮件发送成功: {}", String.join(",", toArr));
            return true;
        } catch (Exception e) {
            log.error("告警邮件发送失败", e);
            return false;
        }
    }

    /**
     * 验证验证码
     */
    public boolean verifyCode(String email, String code) {
        VerificationCode stored = codeCache.get(email);
        if (stored == null) {
            return false;
        }
        
        if (stored.isExpired()) {
            codeCache.remove(email);
            return false;
        }
        
        boolean valid = stored.getCode().equals(code);
        if (valid) {
            codeCache.remove(email); // 验证成功后删除
        }
        return valid;
    }

    /**
     * 发送邮件
     */
    private boolean sendEmail(String to, String subject, String content) {
        try {
            if (fromEmail == null || fromEmail.isEmpty()) {
                log.warn("邮件服务未配置，跳过发送");
                return false;
            }
            
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);
            message.setSentDate(new java.util.Date());
            
            mailSender.send(message);
            log.info("邮件发送成功: {}", to);
            return true;
        } catch (Exception e) {
            log.error("邮件发送失败: {}", to, e);
            return false;
        }
    }

    private String generateCode() {
        Random random = new Random();
        return String.format("%06d", random.nextInt(1000000));
    }

    /**
     * 验证码对象
     */
    private static class VerificationCode {
        private final String code;
        private final String type;
        private final long createTime;
        private int attempts;

        public VerificationCode(String code, String type) {
            this.code = code;
            this.type = type;
            this.createTime = System.currentTimeMillis();
            this.attempts = 0;
        }

        public String getCode() { return code; }
        public String getType() { return type; }
        public int getAttempts() { return attempts; }
        
        public void incrementAttempts() { this.attempts++; }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - createTime > CODE_EXPIRE_MINUTES * 60 * 1000;
        }
    }
}
