package com.adlin.orin.modules.system.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_log_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogConfig {

    @Id
    @Column(name = "config_key")
    private String configKey;

    @Column(name = "config_value")
    private String configValue;

    @Column(name = "description")
    private String description;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
