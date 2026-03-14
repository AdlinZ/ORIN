package com.adlin.orin.modules.system.service;

import com.adlin.orin.modules.system.entity.MailConfigEntity;
import com.adlin.orin.modules.system.entity.MailSendLog;
import com.adlin.orin.modules.system.repository.MailConfigRepository;
import com.adlin.orin.modules.system.repository.MailSendLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
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
public class MailService {

    private final JavaMailSender mailSender;
    private final MailConfigRepository mailConfigRepository;
    private final MailSendLogRepository mailSendLogRepository;
    private final RestTemplate restTemplate;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${app.name:ORIN}")
    private String appName;

    private static final String MAILERSEND_API_URL = "https://api.mailersend.com/v1/email";
    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    // 验证码缓存 (email -> code)
    private final Map<String, VerificationCode> codeCache = new ConcurrentHashMap<>();
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    private static final int CODE_EXPIRE_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 5;

    public MailService(JavaMailSender mailSender,
                       MailConfigRepository mailConfigRepository,
                       MailSendLogRepository mailSendLogRepository,
                       RestTemplate restTemplate) {
        this.mailSender = mailSender;
        this.mailConfigRepository = mailConfigRepository;
        this.mailSendLogRepository = mailSendLogRepository;
        this.restTemplate = restTemplate;
    }

    /**
     * 获取当前邮件配置
     */
    private MailConfigEntity getMailConfig() {
        return mailConfigRepository.findFirstByEnabledTrueOrderByUpdatedAtDesc().orElse(null);
    }

    /**
     * 获取原始邮件配置（不掩码）
     */
    private MailConfigEntity getRawMailConfig() {
        MailConfigEntity config = mailConfigRepository.findFirstByEnabledTrueOrderByUpdatedAtDesc().orElse(null);
        if (config != null) {
            // 如果配置被缓存掩码了，重新从数据库获取
            // 这里返回原始配置，调用方需要注意不返回给前端
            return config;
        }
        return null;
    }

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
        MailConfigEntity config = getRawMailConfig();
        String recipients = String.join(",", toArr);
        String fullSubject = "[" + appName + " 告警] " + subject;

        // 创建日志记录
        MailSendLog logEntry = new MailSendLog();
        logEntry.setSubject(fullSubject);
        logEntry.setRecipients(recipients);
        logEntry.setContent(content);
        logEntry.setMailerType(config != null ? config.getMailerType() : "smtp");

        boolean success = false;
        String errorMessage = null;

        if (config != null && config.getEnabled() != null && config.getEnabled()) {
            if ("mailersend".equals(config.getMailerType())) {
                // MailerSend 批量发送
                success = sendAlertEmailViaMailerSend(toArr, subject, content, config);
            }
        }

        // 默认 SMTP 发送
        if (!success) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                String from = config != null && config.getFromEmail() != null
                    ? config.getFromEmail() : fromEmail;
                String fromName = config != null && config.getFromName() != null
                    ? config.getFromName() : appName;

                message.setFrom(fromName + " <" + from + ">");
                message.setTo(toArr);
                message.setSubject(fullSubject);
                message.setText(content);
                message.setSentDate(new java.util.Date());

                mailSender.send(message);
                success = true;
                log.info("告警邮件发送成功: {}", recipients);
            } catch (Exception e) {
                errorMessage = e.getMessage();
                log.error("告警邮件发送失败", e);
            }
        }

        // 记录日志
        logEntry.setStatus(success ? MailSendLog.STATUS_SUCCESS : MailSendLog.STATUS_FAILED);
        logEntry.setErrorMessage(errorMessage);
        try {
            mailSendLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("保存邮件日志失败", e);
        }

        return success;
    }

    /**
     * 通过 MailerSend 发送告警邮件（批量）
     */
    private boolean sendAlertEmailViaMailerSend(String[] toArr, String subject, String content, MailConfigEntity config) {
        try {
            String apiKey = config.getApiKey();
            String fromEmail = config.getFromEmail();
            String fromName = config.getFromName();

            if (apiKey == null || apiKey.isEmpty() || fromEmail == null || fromEmail.isEmpty()) {
                log.warn("MailerSend配置不完整，跳过发送");
                return false;
            }

            Map<String, Object> requestBody = new HashMap<>();

            // 发件人
            Map<String, String> from = new HashMap<>();
            from.put("email", fromEmail);
            if (fromName != null && !fromName.isEmpty()) {
                from.put("name", fromName);
            }
            requestBody.put("from", from);

            // 收件人（批量）
            List<Map<String, String>> toList = new ArrayList<>();
            for (String to : toArr) {
                Map<String, String> recipient = new HashMap<>();
                recipient.put("email", to);
                toList.add(recipient);
            }
            requestBody.put("to", toList);

            // 主题和内容
            requestBody.put("subject", "[" + appName + " 告警] " + subject);
            requestBody.put("text", content);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                MAILERSEND_API_URL,
                HttpMethod.POST,
                entity,
                String.class
            );

            if (response.getStatusCode() == HttpStatus.ACCEPTED) {
                log.info("MailerSend告警邮件发送成功: {}", String.join(",", toArr));
                return true;
            } else {
                log.error("MailerSend告警邮件发送失败: {}, status: {}", String.join(",", toArr), response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("MailerSend告警邮件发送异常: {}", String.join(",", toArr), e);
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
        MailConfigEntity config = getRawMailConfig();

        // 如果有数据库配置，优先使用数据库配置
        if (config != null && config.getEnabled() != null && config.getEnabled()) {
            if ("mailersend".equals(config.getMailerType())) {
                return sendEmailViaMailerSend(to, subject, content, config);
            } else if ("resend".equals(config.getMailerType())) {
                return sendEmailViaResend(to, subject, content, config);
            } else {
                return sendEmailViaSmtp(to, subject, content, config);
            }
        }

        // 否则使用配置文件
        if (fromEmail == null || fromEmail.isEmpty()) {
            log.warn("邮件服务未配置，跳过发送");
            return false;
        }

        return sendEmailViaSmtp(to, subject, content, null);
    }

    /**
     * 通过 SMTP 发送邮件
     */
    private boolean sendEmailViaSmtp(String to, String subject, String content, MailConfigEntity config) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            String from = config != null ? config.getFromEmail() : fromEmail;
            String fromName = config != null ? config.getFromName() : appName;

            message.setFrom(fromName + " <" + from + ">");
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);
            message.setSentDate(new java.util.Date());

            mailSender.send(message);
            log.info("SMTP邮件发送成功: {}", to);
            return true;
        } catch (Exception e) {
            log.error("SMTP邮件发送失败: {}", to, e);
            return false;
        }
    }

    /**
     * 通过 MailerSend API 发送邮件
     */
    private boolean sendEmailViaMailerSend(String to, String subject, String content, MailConfigEntity config) {
        try {
            String apiKey = config.getApiKey();
            String fromEmail = config.getFromEmail();
            String fromName = config.getFromName();

            if (apiKey == null || apiKey.isEmpty() || fromEmail == null || fromEmail.isEmpty()) {
                log.warn("MailerSend配置不完整，跳过发送");
                return false;
            }

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();

            // 发件人
            Map<String, String> from = new HashMap<>();
            from.put("email", fromEmail);
            if (fromName != null && !fromName.isEmpty()) {
                from.put("name", fromName);
            }
            requestBody.put("from", from);

            // 收件人
            List<Map<String, String>> toList = new ArrayList<>();
            Map<String, String> recipient = new HashMap<>();
            recipient.put("email", to);
            toList.add(recipient);
            requestBody.put("to", toList);

            // 主题和内容
            requestBody.put("subject", subject);
            requestBody.put("text", content);

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                MAILERSEND_API_URL,
                HttpMethod.POST,
                entity,
                String.class
            );

            if (response.getStatusCode() == HttpStatus.ACCEPTED) {
                log.info("MailerSend邮件发送成功: {}", to);
                return true;
            } else {
                log.error("MailerSend邮件发送失败: {}, status: {}", to, response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("MailerSend邮件发送异常: {}", to, e);
            return false;
        }
    }

    /**
     * 通过 Resend API 发送邮件
     */
    private boolean sendEmailViaResend(String to, String subject, String content, MailConfigEntity config) {
        try {
            String apiKey = config.getApiKey();
            String fromEmail = config.getFromEmail();
            String fromName = config.getFromName();

            if (apiKey == null || apiKey.isEmpty() || fromEmail == null || fromEmail.isEmpty()) {
                log.warn("Resend配置不完整，跳过发送");
                return false;
            }

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();

            // 发件人
            requestBody.put("from", fromName != null && !fromName.isEmpty()
                ? fromName + " <" + fromEmail + ">"
                : fromEmail);

            // 收件人
            requestBody.put("to", to);

            // 主题和内容
            requestBody.put("subject", subject);
            requestBody.put("text", content);

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                RESEND_API_URL,
                HttpMethod.POST,
                entity,
                String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Resend邮件发送成功: {}", to);
                return true;
            } else {
                log.error("Resend邮件发送失败: {}, status: {}, body: {}", to, response.getStatusCode(), response.getBody());
                return false;
            }
        } catch (Exception e) {
            log.error("Resend邮件发送异常: {}", to, e);
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
