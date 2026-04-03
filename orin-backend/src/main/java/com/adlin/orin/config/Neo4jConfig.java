package com.adlin.orin.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Map;

@Configuration
@Slf4j
public class Neo4jConfig {

    @Value("${neo4j.host:localhost}")
    private String host;

    @Value("${neo4j.port:7687}")
    private int port;

    @Value("${neo4j.uri:}")
    private String uri;

    @Value("${neo4j.username:neo4j}")
    private String username;

    @Value("${neo4j.password:}")
    private String password;

    @Value("${neo4j.database:neo4j}")
    private String database;

    @Value("${neo4j.max-connection-pool-size:50}")
    private int maxConnectionPoolSize;

    @Value("${neo4j.connection-acquisition-timeout-ms:60000}")
    private long connectionAcquisitionTimeoutMs;

    @Bean
    public Neo4jConnectionManager neo4jConnectionManager() {
        if (!StringUtils.hasText(password)) {
            log.warn("Neo4j password is not configured. Graph features may not work properly.");
        }
        String effectiveUri = StringUtils.hasText(uri) ? uri : String.format("bolt://%s:%d", host, port);
        log.info("Configuring Neo4j connection: {}/{}", effectiveUri, database);
        return new Neo4jConnectionManager(effectiveUri, host, port, username, password, database,
                maxConnectionPoolSize, connectionAcquisitionTimeoutMs);
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class Neo4jConnectionManager implements AutoCloseable {
        private String uri;
        private String host;
        private int port;
        private String username;
        private String password;
        private String database;
        private int maxConnectionPoolSize;
        private long connectionAcquisitionTimeoutMs;

        public String getBoltUri() {
            if (StringUtils.hasText(uri)) {
                return uri;
            }
            return String.format("bolt://%s:%d", host, port);
        }

        public Map<String, Object> toConfig() {
            return Map.of(
                "host", host,
                "port", port,
                "database", database,
                "maxConnectionPoolSize", maxConnectionPoolSize,
                "connectionAcquisitionTimeoutMs", connectionAcquisitionTimeoutMs
            );
        }

        @Override
        public void close() {
            log.info("Neo4j connection manager closed");
        }
    }
}
