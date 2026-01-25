package com.adlin.orin.modules.monitor.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "mon_prometheus_config")
public class PrometheusConfig {
    @Id
    private String id; // 默认使用 "DEFAULT"
    private String prometheusUrl;
    private Boolean enabled;
}
