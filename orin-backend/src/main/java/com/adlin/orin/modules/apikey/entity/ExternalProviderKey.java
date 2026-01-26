package com.adlin.orin.modules.apikey.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 外部供应商API密钥
 */
@Entity
@Table(name = "external_provider_keys")
@Data
public class ExternalProviderKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String provider; // OpenAI, DeepSeek, SiliconFlow, etc.

    @Column(nullable = false)
    private String apiKey; // Plain text or encrypted. For simplicity now, store as is, or use simple mask.

    private String baseUrl;

    private String description;

    @Column(nullable = false)
    private Boolean enabled = true;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
