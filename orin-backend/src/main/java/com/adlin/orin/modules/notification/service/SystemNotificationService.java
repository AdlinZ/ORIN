package com.adlin.orin.modules.notification.service;

import com.adlin.orin.modules.notification.entity.SystemMessage;
import com.adlin.orin.modules.notification.repository.SystemMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * 系统消息服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemNotificationService {

    private final SystemMessageRepository messageRepository;

    /**
     * 发送消息
     */
    @Transactional
    public SystemMessage sendMessage(String title, String content, String type, String receiverId, String senderId) {
        String scope = (receiverId == null || receiverId.isEmpty()) ? "BROADCAST" : "USER";

        SystemMessage message = SystemMessage.builder()
                .title(title)
                .content(content)
                .type(type)
                .receiverId(receiverId)
                .senderId(senderId)
                .scope(scope)
                .read(false)
                .build();

        SystemMessage saved = messageRepository.save(message);
        return saved;
    }

    /**
     * 发送消息（支持指定范围）
     */
    @Transactional
    public SystemMessage sendMessage(String title, String content, String type, String receiverId, String senderId, String scope) {
        SystemMessage message = SystemMessage.builder()
                .title(title)
                .content(content)
                .type(type)
                .receiverId(receiverId)
                .senderId(senderId)
                .scope(scope)
                .read(false)
                .build();

        SystemMessage saved = messageRepository.save(message);
        log.info("Message sent: {} to {}", title, receiverId != null ? receiverId : "broadcast");
        return saved;
    }

    /**
     * 发送系统通知
     */
    public SystemMessage sendSystemNotification(String title, String content) {
        return sendMessage(title, content, "SYSTEM", null, "SYSTEM");
    }

    /**
     * 发送警告消息
     */
    public SystemMessage sendWarning(String title, String content, String receiverId) {
        return sendMessage(title, content, "WARNING", receiverId, "SYSTEM");
    }

    /**
     * 发送错误消息
     */
    public SystemMessage sendError(String title, String content, String receiverId) {
        return sendMessage(title, content, "ERROR", receiverId, "SYSTEM");
    }

    /**
     * 获取用户消息列表（支持按范围过滤）
     */
    public Page<SystemMessage> getUserMessages(String userId, int page, int size, String scope) {
        if (scope != null && "USER".equals(scope)) {
            // 只返回用户消息
            return messageRepository.findByReceiverIdAndScope(userId, "USER",
                    PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        }
        // 返回所有消息（用户消息 + 广播）
        return messageRepository.findByUserMessages(userId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    /**
     * 获取广播消息列表
     */
    public Page<SystemMessage> getBroadcasts(int page, int size) {
        return messageRepository.findByScope("BROADCAST",
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    /**
     * 获取未读消息数
     */
    public long getUnreadCount(String userId) {
        return messageRepository.countUnreadByUser(userId);
    }

    /**
     * 标记消息为已读
     */
    @Transactional
    public boolean markAsRead(Long messageId, String userId) {
        return messageRepository.markAsRead(messageId, userId) > 0;
    }

    /**
     * 标记所有消息为已读
     */
    @Transactional
    public int markAllAsRead(String userId) {
        return messageRepository.markAllAsRead(userId);
    }

    /**
     * 获取消息详情
     */
    public Optional<SystemMessage> getMessage(Long messageId) {
        return messageRepository.findById(messageId);
    }

    /**
     * 清理过期消息
     */
    @Transactional
    public int cleanupExpiredMessages() {
        return messageRepository.deleteExpiredMessages();
    }

    /**
     * 获取消息统计
     */
    public Map<String, Object> getMessageStats(String userId) {
        return Map.of(
                "unread", messageRepository.countUnreadByUser(userId)
        );
    }
}
