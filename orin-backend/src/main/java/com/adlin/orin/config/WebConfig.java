package com.adlin.orin.config;

import com.adlin.orin.security.ApiKeyAuthInterceptor;
import com.adlin.orin.security.ApiRateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC配置
 */
@Configuration
@EnableAsync
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final ApiKeyAuthInterceptor apiKeyAuthInterceptor;
    private final ApiRateLimitInterceptor apiRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 添加API密钥验证拦截器(优先级最高)
        registry.addInterceptor(apiKeyAuthInterceptor)
                .addPathPatterns("/v1/**")
                .excludePathPatterns("/v1/health", "/v1/providers")
                .order(1);

        // 添加速率限制拦截器(在API密钥验证之后)
        registry.addInterceptor(apiRateLimitInterceptor)
                .addPathPatterns("/v1/**")
                .excludePathPatterns("/v1/health", "/v1/providers")
                .order(2);
    }
}
