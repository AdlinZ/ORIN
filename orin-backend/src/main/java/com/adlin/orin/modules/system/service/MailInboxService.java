package com.adlin.orin.modules.system.service;

import com.adlin.orin.modules.system.entity.MailConfigEntity;
import com.adlin.orin.modules.system.entity.MailInboxEntity;
import com.adlin.orin.modules.system.repository.MailInboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.Optional;
import java.util.Properties;

/**
 * 邮件收件箱服务
 */
@Slf4j
@Service
public class MailInboxService {

    private final MailInboxRepository mailInboxRepository;
    private final MailConfigService mailConfigService;

    public MailInboxService(MailInboxRepository mailInboxRepository,
                           MailConfigService mailConfigService) {
        this.mailInboxRepository = mailInboxRepository;
        this.mailConfigService = mailConfigService;
    }

    /**
     * 获取收件箱列表
     */
    public Page<MailInboxEntity> getInboxList(Pageable pageable) {
        return mailInboxRepository.findByFolderOrderByReceivedAtDesc("INBOX", pageable);
    }

    /**
     * 获取未读邮件数量
     */
    public long getUnreadCount() {
        return mailInboxRepository.countByIsReadFalse();
    }

    /**
     * 根据ID获取邮件详情
     */
    public Optional<MailInboxEntity> getById(Long id) {
        return mailInboxRepository.findById(id);
    }

    /**
     * 标记邮件为已读
     */
    @Transactional
    public boolean markAsRead(Long id) {
        return mailInboxRepository.findById(id)
                .map(mail -> {
                    mail.setIsRead(true);
                    mailInboxRepository.save(mail);
                    log.info("邮件已标记为已读: {}", id);
                    return true;
                })
                .orElse(false);
    }

    /**
     * 标记邮件为星标
     */
    @Transactional
    public boolean markAsStarred(Long id, boolean starred) {
        return mailInboxRepository.findById(id)
                .map(mail -> {
                    mail.setIsStarred(starred);
                    mailInboxRepository.save(mail);
                    log.info("邮件星标状态已更新: {}, starred: {}", id, starred);
                    return true;
                })
                .orElse(false);
    }

    /**
     * 删除邮件
     */
    @Transactional
    public boolean delete(Long id) {
        return mailInboxRepository.findById(id)
                .map(mail -> {
                    mailInboxRepository.delete(mail);
                    log.info("邮件已删除: {}", id);
                    return true;
                })
                .orElse(false);
    }

    /**
     * 通过 IMAP 拉取邮件
     */
    @Transactional
    public int fetchEmails() {
        Optional<MailConfigEntity> configOpt = mailConfigService.getConfig();

        if (configOpt.isEmpty()) {
            log.warn("邮件配置不存在");
            return 0;
        }

        MailConfigEntity config = configOpt.get();

        if (!config.getImapEnabled() || config.getImapHost() == null) {
            log.warn("IMAP 未启用或未配置");
            return 0;
        }

        // 如果 IMAP 密码是掩码，从数据库获取原始值
        String imapPassword = config.getImapPassword();
        if (imapPassword != null && imapPassword.startsWith("••")) {
            log.warn("IMAP 密码未配置");
            return 0;
        }

        try {
            return fetchEmailsFromImap(config, imapPassword);
        } catch (Exception e) {
            log.error("IMAP 拉取邮件失败: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 从 IMAP 服务器拉取邮件
     */
    private int fetchEmailsFromImap(MailConfigEntity config, String imapPassword) throws MessagingException, IOException {
        Properties props = new Properties();
        props.setProperty("mail.store.protocol", "imaps");
        props.setProperty("mail.imaps.host", config.getImapHost());
        props.setProperty("mail.imaps.port", String.valueOf(config.getImapPort() != null ? config.getImapPort() : 993));
        props.setProperty("mail.imaps.ssl.enable", "true");

        Session emailSession = Session.getInstance(props);
        Store store = emailSession.getStore("imaps");
        store.connect(config.getImapUsername(), imapPassword);

        // 打开收件箱
        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_ONLY);

        int fetchedCount = 0;
        int maxFetch = 50; // 每次最多拉取50封邮件

        // 获取邮件
        Message[] messages = inbox.getMessages();
        int startIndex = Math.max(0, messages.length - maxFetch);

        for (int i = startIndex; i < messages.length; i++) {
            try {
                Message message = messages[i];
                String messageId = getMessageId(message);

                // 检查是否已存在
                if (messageId != null && !mailInboxRepository.existsByMessageId(messageId)) {
                    MailInboxEntity inboxEntity = convertToEntity(message, messageId);
                    mailInboxRepository.save(inboxEntity);
                    fetchedCount++;
                }
            } catch (Exception e) {
                log.warn("处理邮件失败: {}", e.getMessage());
            }
        }

        inbox.close(false);
        store.close();

        log.info("IMAP 拉取邮件完成, 新增: {}", fetchedCount);
        return fetchedCount;
    }

    /**
     * 获取邮件的 Message-ID
     */
    private String getMessageId(Message message) throws MessagingException {
        String[] messageIds = message.getHeader("Message-ID");
        if (messageIds != null && messageIds.length > 0) {
            return messageIds[0];
        }
        return null;
    }

    /**
     * 将 Message 转换为实体
     */
    private MailInboxEntity convertToEntity(Message message, String messageId) throws Exception {
        MimeMessage mimeMessage = (MimeMessage) message;

        MailInboxEntity entity = MailInboxEntity.builder()
                .messageId(messageId)
                .subject(mimeMessage.getSubject() != null ? mimeMessage.getSubject() : "(无主题)")
                .isRead(false)
                .isStarred(false)
                .folder("INBOX")
                .build();

        // 发件人
        Address[] fromAddresses = message.getFrom();
        if (fromAddresses != null && fromAddresses.length > 0) {
            String from = fromAddresses[0].toString();
            int nameStart = from.indexOf('<');
            int nameEnd = from.indexOf('>');
            if (nameStart > 0 && nameEnd > nameStart) {
                entity.setFromName(from.substring(0, nameStart).trim());
                entity.setFromEmail(from.substring(nameStart + 1, nameEnd).trim());
            } else {
                entity.setFromEmail(from);
            }
        }

        // 收件人
        Address[] toAddresses = message.getAllRecipients();
        if (toAddresses != null && toAddresses.length > 0) {
            entity.setToEmail(toAddresses[0].toString());
        }

        // 接收时间
        if (message.getSentDate() != null) {
            entity.setReceivedAt(message.getSentDate().toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime());
        }

        // 邮件内容
        try {
            String content = message.getContent().toString();
            entity.setContent(content);
            if (message.isMimeType("text/html")) {
                entity.setContentHtml(content);
            }
        } catch (Exception e) {
            log.debug("获取邮件内容失败: {}", e.getMessage());
        }

        return entity;
    }

    /**
     * 获取 IMAP 配置状态
     */
    public boolean isImapConfigured() {
        Optional<MailConfigEntity> configOpt = mailConfigService.getConfig();
        if (configOpt.isEmpty()) {
            return false;
        }
        MailConfigEntity config = configOpt.get();
        return config.getImapEnabled() != null
            && config.getImapEnabled()
            && config.getImapHost() != null
            && config.getImapUsername() != null;
    }
}
