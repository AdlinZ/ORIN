package com.adlin.orin.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS);
        objectMapper.getFactory().setStreamReadConstraints(
                StreamReadConstraints.builder().maxStringLength(50_000_000).build());
        return objectMapper;
    }

    @Bean
    public Jackson2ObjectMapperBuilder jackson2ObjectMapperBuilder() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        builder.serializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS);
        builder.postConfigurer(objectMapper -> {
            objectMapper.getFactory().setStreamReadConstraints(
                    StreamReadConstraints.builder().maxStringLength(50_000_000).build());
            // 注册 Java 8 时间模块
            objectMapper.registerModule(new JavaTimeModule());
            // 禁用将日期序列化为时间戳
            objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        });
        return builder;
    }
}
