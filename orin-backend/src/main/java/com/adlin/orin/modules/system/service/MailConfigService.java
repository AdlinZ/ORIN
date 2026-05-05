package com.adlin.orin.modules.system.service;

import com.adlin.orin.modules.system.entity.MailConfigEntity;
import com.adlin.orin.modules.system.entity.MailSendLog;
import com.adlin.orin.modules.system.repository.MailConfigRepository;
import com.adlin.orin.modules.system.repository.MailSendLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
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
    private final MailSendLogRepository mailSendLogRepository;
    private final JavaMailSender mailSender;
    private final RestTemplate restTemplate;

    private static final String MAILERSEND_API_URL = "https://api.mailersend.com/v1/email";
    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    public MailConfigService(MailConfigRepository mailConfigRepository,
                            MailSendLogRepository mailSendLogRepository,
                            JavaMailSender mailSender,
                            RestTemplate restTemplate) {
        this.mailConfigRepository = mailConfigRepository;
        this.mailSendLogRepository = mailSendLogRepository;
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

        Optional<MailConfigEntity> oldConfig = config.getId() != null
                ? mailConfigRepository.findById(config.getId())
                : mailConfigRepository.findFirstByEnabledTrueOrderByUpdatedAtDesc();

        if (oldConfig.isPresent()) {
            MailConfigEntity existing = oldConfig.get();
            if (isMasked(config.getApiKey())) {
                log.info("检测到API Key为掩码，从数据库获取旧值: mailerType={}", existing.getMailerType());
                config.setApiKey(existing.getApiKey());
            }
            if (isMasked(config.getPassword())) {
                log.info("检测到SMTP密码为掩码，从数据库获取旧值: mailerType={}", existing.getMailerType());
                config.setPassword(existing.getPassword());
            }
            if (isMasked(config.getImapPassword())) {
                log.info("检测到IMAP密码为掩码，从数据库获取旧值: mailerType={}", existing.getMailerType());
                config.setImapPassword(existing.getImapPassword());
            }
        }

        if (config.getEnabled() == null) {
            config.setEnabled(true);
        }
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
        hydrateMaskedSecrets(config);

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
    public boolean sendMail(MailConfigEntity config, String to, String cc, String bcc, String subject, String content, boolean separateSend) {
        if (config == null || !config.getEnabled()) {
            log.error("邮件服务未配置");
            return false;
        }

        List<String> toRecipients = parseRecipients(to);
        List<String> ccRecipients = parseRecipients(cc);
        List<String> bccRecipients = parseRecipients(bcc);
        if (toRecipients.isEmpty()) {
            return false;
        }

        // 创建日志记录
        MailSendLog logEntry = new MailSendLog();
        logEntry.setSubject(subject);
        logEntry.setRecipients(buildRecipientsLogText(toRecipients, ccRecipients, bccRecipients));
        logEntry.setContent(content);
        logEntry.setMailerType(config.getMailerType() != null ? config.getMailerType() : "smtp");

        String errorMessage = null;

        // 如果是 MailerSend 模式
        if ("mailersend".equals(config.getMailerType())) {
            errorMessage = sendViaMailerSend(config, toRecipients, ccRecipients, bccRecipients, subject, content, separateSend);
        }
        // 如果是 Resend 模式
        else if ("resend".equals(config.getMailerType())) {
            errorMessage = sendViaResend(config, toRecipients, ccRecipients, bccRecipients, subject, content, separateSend);
        }
        // SMTP 模式
        else {
            errorMessage = sendViaSmtp(config, toRecipients, ccRecipients, bccRecipients, subject, content, separateSend);
        }

        boolean success = errorMessage == null;

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
     * 通过 MailerSend 发送邮件
     * @return 成功返回 null，失败返回错误信息
     */
    private String sendViaMailerSend(MailConfigEntity config,
                                     List<String> toRecipients,
                                     List<String> ccRecipients,
                                     List<String> bccRecipients,
                                     String subject,
                                     String content,
                                     boolean separateSend) {
        try {
            String apiKey = config.getApiKey();
            String fromEmail = config.getFromEmail();
            String fromName = config.getFromName();

            if (apiKey == null || apiKey.isEmpty() || fromEmail == null || fromEmail.isEmpty()) {
                log.error("MailerSend配置不完整");
                return "MailerSend配置不完整";
            }

            List<List<String>> toGroups = splitRecipientsForSend(toRecipients, separateSend);
            for (List<String> toGroup : toGroups) {
                Map<String, Object> requestBody = new HashMap<>();
                Map<String, String> from = new HashMap<>();
                from.put("email", fromEmail);
                if (fromName != null && !fromName.isEmpty()) {
                    from.put("name", fromName);
                }
                requestBody.put("from", from);
                requestBody.put("to", mapRecipientsForMailerSend(toGroup));
                if (!ccRecipients.isEmpty()) requestBody.put("cc", mapRecipientsForMailerSend(ccRecipients));
                if (!bccRecipients.isEmpty()) requestBody.put("bcc", mapRecipientsForMailerSend(bccRecipients));
                requestBody.put("subject", subject);
                requestBody.put("text", stripHtml(content));
                requestBody.put("html", content);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", "Bearer " + apiKey);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                ResponseEntity<String> response = restTemplate.exchange(MAILERSEND_API_URL, HttpMethod.POST, entity, String.class);
                if (!response.getStatusCode().is2xxSuccessful()) {
                    String error = "MailerSend邮件发送失败: " + response.getBody();
                    log.error(error);
                    return error;
                }
            }
            log.info("MailerSend邮件发送成功: {}", String.join(",", toRecipients));
            return null;
        } catch (Exception e) {
            String error = "MailerSend邮件发送异常: " + e.getMessage();
            log.error(error, e);
            return error;
        }
    }

    /**
     * 通过 Resend 发送邮件
     * @return 成功返回 null，失败返回错误信息
     */
    private String sendViaResend(MailConfigEntity config,
                                 List<String> toRecipients,
                                 List<String> ccRecipients,
                                 List<String> bccRecipients,
                                 String subject,
                                 String content,
                                 boolean separateSend) {
        try {
            String apiKey = config.getApiKey();
            String fromEmail = config.getFromEmail();
            String fromName = config.getFromName();

            log.info("Resend发送邮件: fromEmail={}, apiKey={}", fromEmail, apiKey != null ? (apiKey.length() > 10 ? apiKey.substring(0, 10) + "..." : apiKey) : "null");

            if (apiKey == null || apiKey.isEmpty() || fromEmail == null || fromEmail.isEmpty()) {
                log.error("Resend配置不完整");
                return "Resend配置不完整";
            }

            List<List<String>> toGroups = splitRecipientsForSend(toRecipients, separateSend);
            for (List<String> toGroup : toGroups) {
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("from", fromName != null && !fromName.isEmpty()
                    ? fromName + " <" + fromEmail + ">"
                    : fromEmail);
                requestBody.put("to", toGroup);
                if (!ccRecipients.isEmpty()) requestBody.put("cc", ccRecipients);
                if (!bccRecipients.isEmpty()) requestBody.put("bcc", bccRecipients);
                requestBody.put("subject", subject);
                requestBody.put("text", stripHtml(content));
                requestBody.put("html", content);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", "Bearer " + apiKey);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                ResponseEntity<String> response = restTemplate.exchange(RESEND_API_URL, HttpMethod.POST, entity, String.class);

                if (!response.getStatusCode().is2xxSuccessful()) {
                    String error = "Resend邮件发送失败: " + response.getBody();
                    log.error(error);
                    return error;
                }
            }
            log.info("Resend邮件发送成功: {}", String.join(",", toRecipients));
            return null;
        } catch (Exception e) {
            String error = "Resend邮件发送异常: " + e.getMessage();
            log.error(error, e);
            return error;
        }
    }

    /**
     * 通过 SMTP 发送邮件
     * @return 成功返回 null，失败返回错误信息
     */
    private String sendViaSmtp(MailConfigEntity config,
                               List<String> toRecipients,
                               List<String> ccRecipients,
                               List<String> bccRecipients,
                               String subject,
                               String content,
                               boolean separateSend) {
        try {
            List<List<String>> toGroups = splitRecipientsForSend(toRecipients, separateSend);
            for (List<String> toGroup : toGroups) {
                var mimeMessage = mailSender.createMimeMessage();
                var helper = new MimeMessageHelper(mimeMessage, "UTF-8");
                helper.setFrom(config.getFromName() + " <" + config.getFromEmail() + ">");
                helper.setTo(toGroup.toArray(new String[0]));
                if (!ccRecipients.isEmpty()) helper.setCc(ccRecipients.toArray(new String[0]));
                if (!bccRecipients.isEmpty()) helper.setBcc(bccRecipients.toArray(new String[0]));
                helper.setSubject(subject);
                helper.setText(content, true);
                mimeMessage.setSentDate(new Date());
                mailSender.send(mimeMessage);
            }
            log.info("SMTP邮件发送成功: {}", String.join(",", toRecipients));
            return null;
        } catch (Exception e) {
            String error = "SMTP邮件发送失败: " + e.getMessage();
            log.error(error, e);
            return error;
        }
    }

    private List<String> parseRecipients(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split("[,;\\n]"))
            .map(String::trim)
            .filter(item -> !item.isEmpty())
            .distinct()
            .toList();
    }

    private List<Map<String, String>> mapRecipientsForMailerSend(List<String> recipients) {
        List<Map<String, String>> result = new ArrayList<>();
        for (String recipient : recipients) {
            Map<String, String> item = new HashMap<>();
            item.put("email", recipient);
            result.add(item);
        }
        return result;
    }

    private List<List<String>> splitRecipientsForSend(List<String> recipients, boolean separateSend) {
        if (!separateSend) return List.of(recipients);
        List<List<String>> groups = new ArrayList<>();
        for (String recipient : recipients) {
            groups.add(List.of(recipient));
        }
        return groups;
    }

    private String buildRecipientsLogText(List<String> to, List<String> cc, List<String> bcc) {
        return "TO:" + String.join(",", to)
            + (cc.isEmpty() ? "" : " | CC:" + String.join(",", cc))
            + (bcc.isEmpty() ? "" : " | BCC:" + String.join(",", bcc));
    }

    private String stripHtml(String html) {
        if (html == null || html.isBlank()) return "";
        return html.replaceAll("(?s)<[^>]*>", " ").replaceAll("\\s+", " ").trim();
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
            mailSenderImpl.setDefaultEncoding("UTF-8");
            Properties props = new Properties();
            props.put("mail.smtp.auth", true);
            if (Boolean.TRUE.equals(config.getSslEnabled())) {
                props.put("mail.smtp.starttls.enable", true);
                props.put("mail.smtp.ssl.trust", config.getSmtpHost());
            }
            mailSenderImpl.setJavaMailProperties(props);
            log.info("JavaMailSender 配置已更新");
        }
    }

    /**
     * 检查邮件服务是否已配置
     */
    public boolean isConfigured() {
        return mailConfigRepository.findFirstByEnabledTrueOrderByUpdatedAtDesc().isPresent();
    }

    private void hydrateMaskedSecrets(MailConfigEntity config) {
        if (!isMasked(config.getApiKey()) && !isMasked(config.getPassword()) && !isMasked(config.getImapPassword())) {
            return;
        }
        Optional<MailConfigEntity> oldConfig = config.getId() != null
                ? mailConfigRepository.findById(config.getId())
                : mailConfigRepository.findFirstByEnabledTrueOrderByUpdatedAtDesc();
        oldConfig.ifPresent(existing -> {
            if (isMasked(config.getApiKey())) {
                config.setApiKey(existing.getApiKey());
            }
            if (isMasked(config.getPassword())) {
                config.setPassword(existing.getPassword());
            }
            if (isMasked(config.getImapPassword())) {
                config.setImapPassword(existing.getImapPassword());
            }
        });
    }

    private boolean isMasked(String value) {
        return value != null && value.startsWith("••");
    }
}
