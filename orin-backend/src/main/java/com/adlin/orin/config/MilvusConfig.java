package com.adlin.orin.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class MilvusConfig {

    @Value("${milvus.host:localhost}")
    private String host;

    @Value("${milvus.port:19530}")
    private int port;

    @Value("${milvus.token:}")
    private String token;

    @Bean
    public MilvusServiceClient milvusServiceClient() {
        log.info("Connecting to Milvus at {}:{}", host, port);
        ConnectParam.Builder builder = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port);

        if (token != null && !token.isEmpty()) {
            builder.withToken(token);
        }

        return new MilvusServiceClient(builder.build());
    }
}
