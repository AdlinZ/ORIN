package com.adlin.orin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RAGFlowConfig {

    @Value("${ragflow.api.timeout:60000}")
    private int ragflowApiTimeout;

    @Bean
    public RestTemplate ragflowRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(ragflowApiTimeout);
        factory.setReadTimeout(ragflowApiTimeout);

        RestTemplate restTemplate = new RestTemplate(factory);

        // 添加JSON消息转换器
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter();
        restTemplate.getMessageConverters().add(jsonConverter);

        return restTemplate;
    }
}
