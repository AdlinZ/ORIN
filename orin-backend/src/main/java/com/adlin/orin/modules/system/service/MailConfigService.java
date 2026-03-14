package com.adlin.orin.modules.system.service;

import com.adlin.orin.modules.system.entity.MailConfigEntity;
import com.adlin.orin.modules.system.repository.MailConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * 邮件配置管理服务
 */
@Slf4j
@Service
public class MailConfigService {

    private final MailConfigRepository mailConfigRepository;
    private final JavaMailSender mailSender;
    private final RestTemplate restTemplate;

    private static final String MAILERSEND_API_URL = "https://api.mailersend.com/v1/email";
    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    public MailConfigService(MailConfigRepository mailConfigRepository,
                            JavaMailSender mailSender,
                            RestTemplate restTemplate) {
        this.mailConfigRepository = mailConfigRepository;
        this.mailSender = mailSender;
        this.restTemplate = restTemplate;
    }

    /**
     * 获取邮件配置
     */
    public Optional<MailConfigEntity> getConfig() {
        return mailConfigRepository.findFirstByEnabledTrueOrderByUpdatedAtDesc();
    }

    /**
     * 获取所有配置（用于管理页面，密码会被掩码）
     */
    public MailConfigEntity getConfigForAdmin() {
        return mailConfigRepository.findFirstByEnabledTrueOrderByUpdatedAtDesc()
                .map(config -> {
                    log.info("获取配置: mailerType={}, fromEmail={}", config.getMailerType(), config.getFromEmail());
                    // 掩码密码
                    if (config.getPassword() != null && !config.getPassword().isEmpty()) {
                        config.setPassword("••••••••");
                    }
                    // 掩码 API Key
                    if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
                        config.setApiKey("••••••••");
                    }
                    return config;
                })
                .orElse(null);
    }

    /**
     * 保存邮件配置
     */
    public MailConfigEntity saveConfig(MailConfigEntity config) {
        log.info("收到邮件配置保存请求: mailerType={}, apiKey={}, fromEmail={}",
                config.getMailerType(),
                config.getApiKey() != null && config.getApiKey().startsWith("••") ? "••••••••" : config.getApiKey(),
                config.getFromEmail());

        // 如果设置了新密码且不是掩码，则加密存储
        if (config.getPassword() != null && !config.getPassword().startsWith("••")) {
            // 这里可以添加密码加密逻辑，目前明文存储
        }

        // 如果 API Key 是掩码，则获取旧的配置
        if (config.getApiKey() != null && config.getApiKey().startsWith("••")) {
            mailConfigRepository.findFirstByEnabledTrueOrderByUpdatedAtDesc().ifPresent(oldConfig -> {
                log.info("检测到API Key为掩码，从数据库获取旧值: mailerType={}", oldConfig.getMailerType());
                config.setApiKey(oldConfig.getApiKey());
            });
        }

        config.setEnabled(true);
        MailConfigEntity saved = mailConfigRepository.save(config);

        log.info("邮件配置已保存: mailerType={}, id={}", saved.getMailerType(), saved.getId());

        // 更新 JavaMailSender 配置（仅 SMTP 模式）
        if ("smtp".equals(config.getMailerType())) {
            updateMailSender(saved);
        }

        log.info("邮件配置已保存: {}", saved.getUsername());
        return saved;
    }

    /**
     * 测试邮件配置
     */
    public boolean testConfig(MailConfigEntity config, String testEmail) {
        // 如果 API Key 被掩码了，从数据库获取原始值
        if (config.getApiKey() != null && config.getApiKey().startsWith("••")) {
            mailConfigRepository.findFirstByEnabledTrueOrderByUpdatedAtDesc().ifPresent(oldConfig -> {
                config.setApiKey(oldConfig.getApiKey());
            });
        }

        // 如果是 MailerSend 模式
        if ("mailersend".equals(config.getMailerType())) {
            return testMailerSend(config, testEmail);
        }

        // 如果是 Resend 模式
        if ("resend".equals(config.getMailerType())) {
            return testResend(config, testEmail);
        }

        // SMTP 模式
        try {
            // 创建临时 mailSender 进行测试
            JavaMailSenderImpl testSender = new JavaMailSenderImpl();
            testSender.setHost(config.getSmtpHost());
            testSender.setPort(config.getSmtpPort());
            testSender.setUsername(config.getUsername());
            testSender.setPassword(config.getPassword());
            testSender.setDefaultEncoding("UTF-8");

            // 配置 SSL
            if (config.getSslEnabled()) {
                java.util.Properties props = new java.util.Properties();
                props.put("mail.smtp.auth", true);
                props.put("mail.smtp.starttls.enable", true);
                props.put("mail.smtp.ssl.trust", config.getSmtpHost());
                testSender.setJavaMailProperties(props);
            }

            // 发送测试邮件
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(config.getFromEmail() != null ? config.getFromEmail() : config.getUsername());
            message.setTo(testEmail);
            message.setSubject("ORIN 邮件服务测试");
            message.setText("这是一封测试邮件，证明邮件服务配置正确。\n\n发送时间: " + java.time.LocalDateTime.now());
            message.setSentDate(new java.util.Date());

            testSender.send(message);
            log.info("SMTP邮件测试成功: {}", testEmail);
            return true;
        } catch (Exception e) {
            log.error("SMTP邮件测试失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 测试 MailerSend 配置
     */
    private boolean testMailerSend(MailConfigEntity config, String testEmail) {
        try {
            String apiKey = config.getApiKey();
            String fromEmail = config.getFromEmail();
            String fromName = config.getFromName();

            if (apiKey == null || apiKey.isEmpty() || fromEmail == null || fromEmail.isEmpty()) {
                log.error("MailerSend配置不完整");
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

            // 收件人
            List<Map<String, String>> toList = new ArrayList<>();
            Map<String, String> recipient = new HashMap<>();
            recipient.put("email", testEmail);
            toList.add(recipient);
            requestBody.put("to", toList);

            requestBody.put("subject", "ORIN 邮件服务测试");
            requestBody.put("text", "这是一封测试邮件，证明邮件服务配置正确。\n\n发送时间: " + java.time.LocalDateTime.now());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.info("准备发送邮件测试, from: {}, to: {}", fromEmail, testEmail);

            ResponseEntity<String> response = restTemplate.exchange(
                MAILERSEND_API_URL,
                HttpMethod.POST,
                entity,
                String.class
            );

            log.info("MailerSend响应: status={}, body={}", response.getStatusCode(), response.getBody());

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("MailerSend邮件测试成功: {}, status: {}", testEmail, response.getStatusCode());
                return true;
            } else {
                log.error("MailerSend邮件测试失败: status {}, body: {}", response.getStatusCode(), response.getBody());
                return false;
            }
        } catch (Exception e) {
            log.error("MailerSend邮件测试异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 测试 Resend 配置
     */
    private boolean testResend(MailConfigEntity config, String testEmail) {
        try {
            String apiKey = config.getApiKey();
            String fromEmail = config.getFromEmail();
            String fromName = config.getFromName();

            if (apiKey == null || apiKey.isEmpty() || fromEmail == null || fromEmail.isEmpty()) {
                log.error("Resend配置不完整");
                return false;
            }

            Map<String, Object> requestBody = new HashMap<>();

            // 发件人
            requestBody.put("from", fromName != null && !fromName.isEmpty()
                ? fromName + " <" + fromEmail + ">"
                : fromEmail);

            // 收件人
            requestBody.put("to", testEmail);

            requestBody.put("subject", "ORIN 邮件服务测试");
            requestBody.put("text", "这是一封测试邮件，证明邮件服务配置正确。\n\n发送时间: " + java.time.LocalDateTime.now());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.info("准备发送Resend测试邮件, from: {}, to: {}", fromEmail, testEmail);

            ResponseEntity<String> response = restTemplate.exchange(
                RESEND_API_URL,
                HttpMethod.POST,
                entity,
                String.class
            );

            log.info("Resend响应: status={}, body={}", response.getStatusCode(), response.getBody());

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Resend邮件测试成功: {}, status: {}", testEmail, response.getStatusCode());
                return true;
            } else {
                log.error("Resend邮件测试失败: status {}, body: {}", response.getStatusCode(), response.getBody());
                return false;
            }
        } catch (Exception e) {
            log.error("Resend邮件测试异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 发送邮件
     */
    public boolean sendMail(MailConfigEntity config, String to, String subject, String content) {
        if (config == null || !config.getEnabled()) {
            log.error("邮件服务未配置");
            return false;
        }

        // 如果是 MailerSend 模式
        if ("mailersend".equals(config.getMailerType())) {
            return sendViaMailerSend(config, to, subject, content);
        }

        // 如果是 Resend 模式
        if ("resend".equals(config.getMailerType())) {
            return sendViaResend(config, to, subject, content);
        }

        // SMTP 模式
        return sendViaSmtp(config, to, subject, content);
    }

    /**
     * 通过 MailerSend 发送邮件
     */
    private boolean sendViaMailerSend(MailConfigEntity config, String to, String subject, String content) {
        try {
            String apiKey = config.getApiKey();
            String fromEmail = config.getFromEmail();
            String fromName = config.getFromName();

            if (apiKey == null || apiKey.isEmpty() || fromEmail == null || fromEmail.isEmpty()) {
                log.error("MailerSend配置不完整");
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

            // 收件人
            List<Map<String, String>> toList = new ArrayList<>();
            Map<String, String> recipient = new HashMap<>();
            recipient.put("email", to);
            toList.add(recipient);
            requestBody.put("to", toList);

            requestBody.put("subject", subject);
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

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("MailerSend邮件发送成功: {}", to);
                return true;
            } else {
                log.error("MailerSend邮件发送失败: {}", response.getBody());
                return false;
            }
        } catch (Exception e) {
            log.error("MailerSend邮件发送异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 通过 Resend 发送邮件
     */
    private boolean sendViaResend(MailConfigEntity config, String to, String subject, String content) {
        try {
            String apiKey = config.getApiKey();
            String fromEmail = config.getFromEmail();
            String fromName = config.getFromName();

            log.info("Resend发送邮件: fromEmail={}, apiKey={}", fromEmail, apiKey != null ? (apiKey.length() > 10 ? apiKey.substring(0, 10) + "..." : apiKey) : "null");

            if (apiKey == null || apiKey.isEmpty() || fromEmail == null || fromEmail.isEmpty()) {
                log.error("Resend配置不完整");
                return false;
            }

            Map<String, Object> requestBody = new HashMap<>();

            // 发件人
            requestBody.put("from", fromName != null && !fromName.isEmpty()
                ? fromName + " <" + fromEmail + ">"
                : fromEmail);

            // 收件人
            requestBody.put("to", to);

            requestBody.put("subject", subject);
            requestBody.put("text", content);

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
                log.error("Resend邮件发送失败: {}", response.getBody());
                return false;
            }
        } catch (Exception e) {
            log.error("Resend邮件发送异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 通过 SMTP 发送邮件
     */
    private boolean sendViaSmtp(MailConfigEntity config, String to, String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(config.getFromName() + " <" + config.getFromEmail() + ">");
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);
            message.setSentDate(new Date());

            mailSender.send(message);
            log.info("SMTP邮件发送成功: {}", to);
            return true;
        } catch (Exception e) {
            log.error("SMTP邮件发送失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 更新 JavaMailSender 配置
     */
    private void updateMailSender(MailConfigEntity config) {
        if (config.getEnabled() && mailSender instanceof JavaMailSenderImpl) {
            JavaMailSenderImpl mailSenderImpl = (JavaMailSenderImpl) mailSender;
            mailSenderImpl.setHost(config.getSmtpHost());
            mailSenderImpl.setPort(config.getSmtpPort());
            mailSenderImpl.setUsername(config.getUsername());
            mailSenderImpl.setPassword(config.getPassword());
            log.info("JavaMailSender 配置已更新");
        }
    }

    /**
     * 检查邮件服务是否已配置
     */
    public boolean isConfigured() {
        return mailConfigRepository.findFirstByEnabledTrueOrderByUpdatedAtDesc().isPresent();
    }
}