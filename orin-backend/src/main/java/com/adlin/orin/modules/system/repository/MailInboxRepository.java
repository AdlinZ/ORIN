package com.adlin.orin.modules.system.repository;

import com.adlin.orin.modules.system.entity.MailInboxEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 邮件收件箱 Repository
 */
@Repository
public interface MailInboxRepository extends JpaRepository<MailInboxEntity, Long> {

    /**
     * 根据 messageId 查询
     */
    Optional<MailInboxEntity> findByMessageId(String messageId);

    /**
     * 根据 messageId 查询是否存在
     */
    boolean existsByMessageId(String messageId);

    /**
     * 分页查询收件箱
     */
    Page<MailInboxEntity> findByFolderOrderByReceivedAtDesc(String folder, Pageable pageable);

    /**
     * 分页查询未读邮件
     */
    Page<MailInboxEntity> findByIsReadFalseOrderByReceivedAtDesc(Pageable pageable);

    /**
     * 统计未读邮件数量
     */
    long countByIsReadFalse();
}
