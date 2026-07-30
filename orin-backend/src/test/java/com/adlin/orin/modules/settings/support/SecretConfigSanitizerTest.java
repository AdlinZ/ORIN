package com.adlin.orin.modules.settings.support;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecretConfigSanitizerTest {

    @Test
    void redactsConfiguredSecretsButKeepsOperationalValues() {
        Map<String, Object> redacted = SecretConfigSanitizer.redactCopy(Map.of(
                "apiUrl", "https://example.test/v1",
                "apiKey", "real-provider-key",
                "password", "real-password",
                "enabled", true
        ));

        assertThat(redacted)
                .containsEntry("apiUrl", "https://example.test/v1")
                .containsEntry("enabled", true)
                .containsEntry("apiKey", SecretConfigSanitizer.MASKED_VALUE)
                .containsEntry("password", SecretConfigSanitizer.MASKED_VALUE);
        assertThat(redacted.values())
                .doesNotContain("real-provider-key", "real-password");
    }

    @Test
    void recognizesEnvironmentStyleSecretKeysWithoutMaskingTokenLimits() {
        assertThat(SecretConfigSanitizer.isSensitiveKey("spring.datasource.password")).isTrue();
        assertThat(SecretConfigSanitizer.isSensitiveKey("storage.minio.secret-key")).isTrue();
        assertThat(SecretConfigSanitizer.isSensitiveKey("milvus.token")).isTrue();
        assertThat(SecretConfigSanitizer.isSensitiveKey("model.max-tokens")).isFalse();
    }

    @Test
    void keepsBlankSecretsBlankSoTheClientCanDistinguishUnconfiguredValues() {
        assertThat(SecretConfigSanitizer.redact("dify.apiKey", "")).isEmpty();
        assertThat(SecretConfigSanitizer.redact("dify.apiKey", null)).isNull();
    }
}
