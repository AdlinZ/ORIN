package com.adlin.orin.modules.monitor.repository;

import com.adlin.orin.modules.monitor.entity.PrometheusConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrometheusConfigRepository extends JpaRepository<PrometheusConfig, String> {
}
