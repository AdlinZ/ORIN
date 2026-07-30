package com.adlin.orin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AiEngineConfig {

    @Value("${orin.ai-engine.url:http://localhost:8000}")
    private String aiEngineUrl;

    @Bean
    public WebClient aiEngineWebClient(WebClient.Builder builder) {
        return builder.baseUrl(aiEngineUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
