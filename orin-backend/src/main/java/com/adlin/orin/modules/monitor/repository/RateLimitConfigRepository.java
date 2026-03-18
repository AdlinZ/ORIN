package com.adlin.orin.modules.monitor.repository;

import com.adlin.orin.modules.monitor.entity.RateLimitConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RateLimitConfigRepository extends JpaRepository<RateLimitConfig, String> {
}
