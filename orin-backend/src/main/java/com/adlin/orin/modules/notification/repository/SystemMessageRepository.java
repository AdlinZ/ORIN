package com.adlin.orin.modules.notification.repository;

import com.adlin.orin.modules.notification.entity.SystemMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemMessageRepository extends JpaRepository<SystemMessage, Long> {

    /**
     * 获取当前用户可见且未清空的消息列表。
     */
    @Query("""
            SELECT m FROM SystemMessage m
            WHERE (m.receiverId = :userId OR m.receiverId IS NULL OR m.scope = 'BROADCAST')
              AND (m.expireAt IS NULL OR m.expireAt > CURRENT_TIMESTAMP)
              AND NOT EXISTS (
                SELECT s FROM SystemMessageUserState s
                WHERE s.messageId = m.id AND s.userId = :userId AND s.dismissedAt IS NOT NULL
              )
            ORDER BY m.createdAt DESC
            """)
    Page<SystemMessage> findVisibleByUser(String userId, Pageable pageable);

    /**
     * 获取用户消息列表
     */
    @Query("""
            SELECT m FROM SystemMessage m
            WHERE m.receiverId = :userId
              AND m.scope = 'USER'
              AND (m.expireAt IS NULL OR m.expireAt > CURRENT_TIMESTAMP)
              AND NOT EXISTS (
                SELECT s FROM SystemMessageUserState s
                WHERE s.messageId = m.id AND s.userId = :userId AND s.dismissedAt IS NOT NULL
              )
            ORDER BY m.createdAt DESC
            """)
    Page<SystemMessage> findUserScopedVisible(String userId, Pageable pageable);

    /**
     * 获取未读消息数
     */
    @Query("""
            SELECT COUNT(m) FROM SystemMessage m
            WHERE (m.receiverId = :userId OR m.receiverId IS NULL OR m.scope = 'BROADCAST')
              AND (m.expireAt IS NULL OR m.expireAt > CURRENT_TIMESTAMP)
              AND (m.read IS NULL OR m.read = false)
              AND NOT EXISTS (
                SELECT s FROM SystemMessageUserState s
                WHERE s.messageId = m.id AND s.userId = :userId
                  AND (s.readAt IS NOT NULL OR s.dismissedAt IS NOT NULL)
              )
            """)
    long countUnreadByUser(String userId);

    @Query("""
            SELECT m.id FROM SystemMessage m
            WHERE (m.receiverId = :userId OR m.receiverId IS NULL OR m.scope = 'BROADCAST')
              AND (m.expireAt IS NULL OR m.expireAt > CURRENT_TIMESTAMP)
              AND NOT EXISTS (
                SELECT s FROM SystemMessageUserState s
                WHERE s.messageId = m.id AND s.userId = :userId AND s.dismissedAt IS NOT NULL
              )
            """)
    List<Long> findVisibleMessageIds(String userId);

    @Query("""
            SELECT m.id FROM SystemMessage m
            WHERE (m.receiverId = :userId OR m.receiverId IS NULL OR m.scope = 'BROADCAST')
              AND (m.expireAt IS NULL OR m.expireAt > CURRENT_TIMESTAMP)
              AND (m.read IS NULL OR m.read = false)
              AND NOT EXISTS (
                SELECT s FROM SystemMessageUserState s
                WHERE s.messageId = m.id AND s.userId = :userId
                  AND (s.readAt IS NOT NULL OR s.dismissedAt IS NOT NULL)
              )
            """)
    List<Long> findUnreadVisibleMessageIds(String userId);

    /**
     * 标记消息为已读
     */
    @Modifying
    @Query("UPDATE SystemMessage m SET m.read = true WHERE m.id = :messageId AND m.receiverId = :userId")
    int markAsRead(Long messageId, String userId);

    /**
     * 标记所有消息为已读
     */
    @Modifying
    @Query("UPDATE SystemMessage m SET m.read = true WHERE m.receiverId = :userId AND m.read = false")
    int markAllAsRead(String userId);

    /**
     * 删除过期消息
     */
    @Modifying
    @Query("DELETE FROM SystemMessage m WHERE m.expireAt IS NOT NULL AND m.expireAt < CURRENT_TIMESTAMP")
    int deleteExpiredMessages();

    /**
     * 按范围查询消息（广播）
     */
    Page<SystemMessage> findByScope(String scope, Pageable pageable);
}
