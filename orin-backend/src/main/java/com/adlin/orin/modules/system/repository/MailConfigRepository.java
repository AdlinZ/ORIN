package com.adlin.orin.modules.system.repository;

import com.adlin.orin.modules.system.entity.MailConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MailConfigRepository extends JpaRepository<MailConfigEntity, Long> {

    /**
     * 获取当前启用的邮件配置
     */
    Optional<MailConfigEntity> findFirstByEnabledTrue();

    /**
     * 获取默认配置（ID=1）
     */
    Optional<MailConfigEntity> findById(Long id);
}