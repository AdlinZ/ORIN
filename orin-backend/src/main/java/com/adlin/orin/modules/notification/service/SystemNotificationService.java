package com.adlin.orin.modules.notification.service;

import com.adlin.orin.modules.notification.entity.SystemMessage;
import com.adlin.orin.modules.notification.entity.SystemMessageUserState;
import com.adlin.orin.modules.notification.repository.SystemMessageRepository;
import com.adlin.orin.modules.notification.repository.SystemMessageUserStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 系统消息服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemNotificationService {

    private final SystemMessageRepository messageRepository;
    private final SystemMessageUserStateRepository stateRepository;

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
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SystemMessage> messages;
        if (scope != null && "USER".equals(scope)) {
            messages = messageRepository.findUserScopedVisible(userId, pageRequest);
        } else {
            messages = messageRepository.findVisibleByUser(userId, pageRequest);
        }
        return withUserReadState(messages, userId);
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
        Optional<SystemMessage> message = messageRepository.findById(messageId)
                .filter(m -> isVisibleToUser(m, userId));
        if (message.isEmpty()) {
            return false;
        }
        markState(messageId, userId, LocalDateTime.now(), null);
        if (userId.equals(message.get().getReceiverId())) {
            messageRepository.markAsRead(messageId, userId);
        }
        return true;
    }

    /**
     * 标记所有消息为已读
     */
    @Transactional
    public int markAllAsRead(String userId) {
        List<Long> ids = messageRepository.findUnreadVisibleMessageIds(userId);
        LocalDateTime now = LocalDateTime.now();
        ids.forEach(id -> markState(id, userId, now, null));
        messageRepository.markAllAsRead(userId);
        return ids.size();
    }

    /**
     * 获取消息详情
     */
    public Optional<SystemMessage> getMessage(Long messageId) {
        return messageRepository.findById(messageId);
    }

    public Optional<SystemMessage> getMessage(Long messageId, String userId) {
        return messageRepository.findById(messageId)
                .filter(message -> isVisibleToUser(message, userId))
                .filter(message -> !isDismissed(message.getId(), userId))
                .map(message -> withUserReadState(message, userId));
    }

    /**
     * 清理过期消息
     */
    @Transactional
    public int cleanupExpiredMessages() {
        return messageRepository.deleteExpiredMessages();
    }

    @Transactional
    public int dismissAllVisibleMessages(String userId) {
        List<Long> ids = messageRepository.findVisibleMessageIds(userId);
        LocalDateTime now = LocalDateTime.now();
        ids.forEach(id -> markState(id, userId, null, now));
        return ids.size();
    }

    /**
     * 获取消息统计
     */
    public Map<String, Object> getMessageStats(String userId) {
        return Map.of(
                "unread", messageRepository.countUnreadByUser(userId)
        );
    }

    private Page<SystemMessage> withUserReadState(Page<SystemMessage> page, String userId) {
        List<Long> ids = page.getContent().stream()
                .map(SystemMessage::getId)
                .toList();
        Map<Long, SystemMessageUserState> stateMap = stateRepository.findByUserIdAndMessageIdIn(userId, ids).stream()
                .collect(Collectors.toMap(SystemMessageUserState::getMessageId, Function.identity()));

        return page.map(message -> {
            SystemMessage copy = copyMessage(message);
            SystemMessageUserState state = stateMap.get(message.getId());
            copy.setRead(Boolean.TRUE.equals(message.getRead()) || (state != null && state.getReadAt() != null));
            return copy;
        });
    }

    private SystemMessage withUserReadState(SystemMessage message, String userId) {
        SystemMessage copy = copyMessage(message);
        stateRepository.findByMessageIdAndUserId(message.getId(), userId)
                .ifPresent(state -> copy.setRead(Boolean.TRUE.equals(message.getRead()) || state.getReadAt() != null));
        return copy;
    }

    private void markState(Long messageId, String userId, LocalDateTime readAt, LocalDateTime dismissedAt) {
        SystemMessageUserState state = stateRepository.findByMessageIdAndUserId(messageId, userId)
                .orElseGet(() -> SystemMessageUserState.builder()
                        .messageId(messageId)
                        .userId(userId)
                        .build());
        if (readAt != null) {
            state.setReadAt(readAt);
        }
        if (dismissedAt != null) {
            state.setDismissedAt(dismissedAt);
        }
        stateRepository.save(state);
    }

    private boolean isVisibleToUser(SystemMessage message, String userId) {
        return userId.equals(message.getReceiverId())
                || message.getReceiverId() == null
                || "BROADCAST".equalsIgnoreCase(message.getScope());
    }

    private boolean isDismissed(Long messageId, String userId) {
        return stateRepository.findByMessageIdAndUserId(messageId, userId)
                .map(SystemMessageUserState::getDismissedAt)
                .isPresent();
    }

    private SystemMessage copyMessage(SystemMessage message) {
        return SystemMessage.builder()
                .id(message.getId())
                .title(message.getTitle())
                .content(message.getContent())
                .type(message.getType())
                .scope(message.getScope())
                .receiverId(message.getReceiverId())
                .senderId(message.getSenderId())
                .read(message.getRead())
                .expireAt(message.getExpireAt())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
