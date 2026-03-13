package com.adlin.orin.modules.system.repository;

import com.adlin.orin.modules.system.entity.MailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MailTemplateRepository extends JpaRepository<MailTemplate, Long> {

    Optional<MailTemplate> findByCode(String code);

    List<MailTemplate> findByEnabledTrue();

    Optional<MailTemplate> findByIsDefaultTrue();

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);
}
