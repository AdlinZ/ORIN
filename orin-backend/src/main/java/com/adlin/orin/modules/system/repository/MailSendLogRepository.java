package com.adlin.orin.modules.system.repository;

import com.adlin.orin.modules.system.entity.MailSendLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 邮件发送日志 Repository
 */
@Repository
public interface MailSendLogRepository extends JpaRepository<MailSendLog, Long> {

    /**
     * 按状态查询
     */
    Page<MailSendLog> findByStatus(String status, Pageable pageable);

    /**
     * 按时间范围查询
     */
    Page<MailSendLog> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    /**
     * 查询最近的日志
     */
    List<MailSendLog> findTop10ByOrderByCreatedAtDesc();
}
