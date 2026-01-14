package com.adlin.orin.modules.system.repository;

import com.adlin.orin.modules.system.entity.LogConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LogConfigRepository extends JpaRepository<LogConfig, String> {
}
