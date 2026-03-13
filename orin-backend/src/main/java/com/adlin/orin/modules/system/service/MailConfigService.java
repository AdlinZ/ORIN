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
        return mailConfigRepository.findFirstByEnabledTrue();
    }

    /**
     * 获取所有配置（用于管理页面，密码会被掩码）
     */
    public MailConfigEntity getConfigForAdmin() {
        return mailConfigRepository.findFirstByEnabledTrue()
                .map(config -> {
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
        // 如果设置了新密码且不是掩码，则加密存储
        if (config.getPassword() != null && !config.getPassword().startsWith("••")) {
            // 这里可以添加密码加密逻辑，目前明文存储
        }

        // 如果 API Key 是掩码，则获取旧的配置
        if (config.getApiKey() != null && config.getApiKey().startsWith("••")) {
            mailConfigRepository.findFirstByEnabledTrue().ifPresent(oldConfig -> {
                config.setApiKey(oldConfig.getApiKey());
            });
        }

        config.setEnabled(true);
        MailConfigEntity saved = mailConfigRepository.save(config);

        // 更新 JavaMailSender 配置（仅 SMTP 模式）
        if (!"mailersend".equals(config.getMailerType())) {
            updateMailSender(saved);
        }

        log.info("邮件配置已保存: {}", saved.getUsername());
        return saved;
    }

    /**
     * 测试邮件配置
     */
    public boolean testConfig(MailConfigEntity config, String testEmail) {
        // 如果是 MailerSend 模式
        if ("mailersend".equals(config.getMailerType())) {
            return testMailerSend(config, testEmail);
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

            ResponseEntity<String> response = restTemplate.exchange(
                MAILERSEND_API_URL,
                HttpMethod.POST,
                entity,
                String.class
            );

            if (response.getStatusCode() == HttpStatus.ACCEPTED) {
                log.info("MailerSend邮件测试成功: {}", testEmail);
                return true;
            } else {
                log.error("MailerSend邮件测试失败: status {}", response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("MailerSend邮件测试异常: {}", e.getMessage());
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
        return mailConfigRepository.findFirstByEnabledTrue().isPresent();
    }
}