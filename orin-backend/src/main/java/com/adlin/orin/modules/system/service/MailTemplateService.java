package com.adlin.orin.modules.system.service;

import com.adlin.orin.modules.system.entity.MailConfigEntity;
import com.adlin.orin.modules.system.entity.MailSendLog;
import com.adlin.orin.modules.system.entity.MailTemplate;
import com.adlin.orin.modules.system.repository.MailConfigRepository;
import com.adlin.orin.modules.system.repository.MailSendLogRepository;
import com.adlin.orin.modules.system.repository.MailTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 邮件模板服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailTemplateService {

    private final MailTemplateRepository templateRepository;
    private final MailConfigRepository mailConfigRepository;
    private final MailSendLogRepository mailSendLogRepository;
    private final JavaMailSender mailSender;
    private final RestTemplate restTemplate;

    private static final String MAILERSEND_API_URL = "https://api.mailersend.com/v1/email";

    /**
     * 获取所有模板
     */
    public List<MailTemplate> getAllTemplates() {
        return templateRepository.findAll();
    }

    /**
     * 获取启用的模板
     */
    public List<MailTemplate> getEnabledTemplates() {
        return templateRepository.findByEnabledTrue();
    }

    /**
     * 根据ID获取模板
     */
    public Optional<MailTemplate> getTemplateById(Long id) {
        return templateRepository.findById(id);
    }

    /**
     * 根据代码获取模板
     */
    public Optional<MailTemplate> getTemplateByCode(String code) {
        return templateRepository.findByCode(code);
    }

    /**
     * 获取默认模板
     */
    public Optional<MailTemplate> getDefaultTemplate() {
        return templateRepository.findByIsDefaultTrue();
    }

    /**
     * 创建模板
     */
    @Transactional
    public MailTemplate createTemplate(MailTemplate template) {
        // 检查模板代码是否已存在
        if (templateRepository.existsByCode(template.getCode())) {
            throw new RuntimeException("模板代码已存在: " + template.getCode());
        }

        // 如果设置为默认模板，先取消其他默认
        if (Boolean.TRUE.equals(template.getIsDefault())) {
            clearDefaultTemplate();
        }

        return templateRepository.save(template);
    }

    /**
     * 更新模板
     */
    @Transactional
    public MailTemplate updateTemplate(Long id, MailTemplate template) {
        MailTemplate existing = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("模板不存在: " + id));

        // 检查模板代码是否与其他模板冲突
        if (templateRepository.existsByCodeAndIdNot(template.getCode(), id)) {
            throw new RuntimeException("模板代码已存在: " + template.getCode());
        }

        // 如果设置为默认模板，先取消其他默认
        if (Boolean.TRUE.equals(template.getIsDefault()) && !Boolean.TRUE.equals(existing.getIsDefault())) {
            clearDefaultTemplate();
        }

        existing.setName(template.getName());
        existing.setCode(template.getCode());
        existing.setSubject(template.getSubject());
        existing.setContent(template.getContent());
        existing.setIsDefault(template.getIsDefault());
        existing.setEnabled(template.getEnabled());

        return templateRepository.save(existing);
    }

    /**
     * 删除模板
     */
    @Transactional
    public void deleteTemplate(Long id) {
        templateRepository.deleteById(id);
    }

    /**
     * 清除默认模板
     */
    @Transactional
    protected void clearDefaultTemplate() {
        templateRepository.findByIsDefaultTrue().ifPresent(template -> {
            template.setIsDefault(false);
            templateRepository.save(template);
        });
    }

    /**
     * 发送批量邮件
     */
    @Transactional
    public Map<String, Object> sendBatchMail(List<String> recipients, Long templateId,
                                               Map<String, String> variables) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, String>> successList = new ArrayList<>();
        List<Map<String, String>> failedList = new ArrayList<>();

        MailTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("模板不存在: " + templateId));

        if (!Boolean.TRUE.equals(template.getEnabled())) {
            throw new RuntimeException("模板已禁用");
        }

        // 处理主题和内容，替换变量
        String subject = processTemplate(template.getSubject(), variables);
        String content = processTemplate(template.getContent(), variables);

        MailConfigEntity config = getMailConfig();
        String mailerType = config != null ? config.getMailerType() : "smtp";

        // 创建日志记录
        MailSendLog logEntry = new MailSendLog();
        logEntry.setSubject(subject);
        logEntry.setRecipients(String.join(",", recipients));
        logEntry.setContent(content);
        logEntry.setMailerType(mailerType);

        boolean success = false;
        String errorMessage = null;

        if (config != null && Boolean.TRUE.equals(config.getEnabled())) {
            if ("mailersend".equals(mailerType)) {
                success = sendBatchViaMailerSend(recipients.toArray(new String[0]), subject, content, config);
            }
        }

        // 如果MailerSend失败，尝试SMTP
        if (!success) {
            try {
                jakarta.mail.internet.MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
                String from = config != null && config.getFromEmail() != null
                    ? config.getFromEmail() : "noreply@example.com";
                String fromName = config != null && config.getFromName() != null
                    ? config.getFromName() : "ORIN系统";
                boolean isHtml = isHtmlContent(content);

                helper.setFrom(fromName + " <" + from + ">");
                helper.setTo(recipients.toArray(new String[0]));
                helper.setSubject(subject);
                helper.setText(content, isHtml);
                message.setSentDate(new java.util.Date());

                mailSender.send(message);
                success = true;
                log.info("批量邮件发送成功: {} 个收件人", recipients.size());
            } catch (Exception e) {
                errorMessage = e.getMessage();
                log.error("批量邮件发送失败", e);
            }
        }

        // 记录结果
        for (String recipient : recipients) {
            Map<String, String> item = new HashMap<>();
            item.put("email", recipient);
            if (success) {
                successList.add(item);
            } else {
                item.put("error", errorMessage);
                failedList.add(item);
            }
        }

        logEntry.setStatus(success ? MailSendLog.STATUS_SUCCESS : MailSendLog.STATUS_FAILED);
        logEntry.setErrorMessage(errorMessage);
        mailSendLogRepository.save(logEntry);

        result.put("success", successList);
        result.put("failed", failedList);
        result.put("total", recipients.size());
        result.put("successCount", successList.size());
        result.put("failedCount", failedList.size());

        return result;
    }

    /**
     * 处理模板变量
     */
    private String processTemplate(String content, Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) {
            return content;
        }

        Pattern pattern = Pattern.compile("\\{\\{(\\w+)\\}\\}");
        Matcher matcher = pattern.matcher(content);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String value = variables.getOrDefault(varName, matcher.group(0));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * 通过 MailerSend 批量发送
     */
    private boolean sendBatchViaMailerSend(String[] toArr, String subject, String content,
                                           MailConfigEntity config) {
        try {
            String apiKey = config.getApiKey();
            String fromEmail = config.getFromEmail();
            String fromName = config.getFromName();

            if (apiKey == null || apiKey.isEmpty() || fromEmail == null || fromEmail.isEmpty()) {
                log.warn("MailerSend配置不完整，跳过发送");
                return false;
            }

            Map<String, Object> requestBody = new HashMap<>();

            Map<String, String> from = new HashMap<>();
            from.put("email", fromEmail);
            if (fromName != null && !fromName.isEmpty()) {
                from.put("name", fromName);
            }
            requestBody.put("from", from);

            List<Map<String, String>> toList = new ArrayList<>();
            for (String to : toArr) {
                Map<String, String> recipient = new HashMap<>();
                recipient.put("email", to);
                toList.add(recipient);
            }
            requestBody.put("to", toList);

            requestBody.put("subject", subject);
            if (isHtmlContent(content)) {
                requestBody.put("html", content);
                requestBody.put("text", stripHtml(content));
            } else {
                requestBody.put("text", content);
            }

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
                log.info("MailerSend批量邮件发送成功: {} 个收件人", toArr.length);
                return true;
            } else {
                log.error("MailerSend批量邮件发送失败: status: {}", response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("MailerSend批量邮件发送异常", e);
            return false;
        }
    }

    private MailConfigEntity getMailConfig() {
        return mailConfigRepository.findAll().stream().findFirst().orElse(null);
    }

    private boolean isHtmlContent(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String trimmed = content.trim();
        return trimmed.startsWith("<!DOCTYPE")
                || trimmed.startsWith("<html")
                || Pattern.compile("<[a-zA-Z][^>]*>").matcher(trimmed).find();
    }

    private String stripHtml(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        return content
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("<[^>]+>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
