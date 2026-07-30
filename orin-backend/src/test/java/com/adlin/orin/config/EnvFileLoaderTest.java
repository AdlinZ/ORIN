package com.adlin.orin.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class EnvFileLoaderTest {

    @Test
    void envFileIsFallbackAndCannotOverrideRuntimeConfiguration() {
        Properties fileProperties = new Properties();
        fileProperties.setProperty("DB_PASSWORD", "local-only-value");
        fileProperties.setProperty("ONLY_IN_ENV_FILE", "available");

        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(
                new MapPropertySource("runtime", Map.of("DB_PASSWORD", "runtime-value")));

        EnvFileLoader loader = new EnvFileLoader() {
            @Override
            Properties loadEnvFile() {
                return fileProperties;
            }
        };

        loader.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("DB_PASSWORD")).isEqualTo("runtime-value");
        assertThat(environment.getProperty("ONLY_IN_ENV_FILE")).isEqualTo("available");
    }
}
