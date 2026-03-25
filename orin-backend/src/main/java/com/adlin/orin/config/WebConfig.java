package com.adlin.orin.config;

import com.adlin.orin.security.ApiKeyAuthInterceptor;
import com.adlin.orin.security.ApiRateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

/**
 * Web MVC配置
 */
@Configuration
@EnableAsync
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final ApiKeyAuthInterceptor apiKeyAuthInterceptor;
    private final ApiRateLimitInterceptor apiRateLimitInterceptor;

    @Value("${orin.security.cors.allowed-origins:}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Parse allowed origins from comma-separated string
        String[] origins = allowedOrigins.isBlank()
            ? new String[]{}
            : allowedOrigins.split(",");

        // Remove wildcards - CORS with * is not allowed for credentials
        for (String origin : origins) {
            if (origin.trim().equals("*")) {
                throw new IllegalStateException(
                    "CORS wildcard '*' is not allowed. Please specify explicit origins in orin.security.cors.allowed-origins");
            }
        }

        registry.addMapping("/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);

        if (origins.length == 0) {
            // No CORS configured - log warning
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 添加API密钥验证拦截器
        registry.addInterceptor(apiKeyAuthInterceptor)
                .addPathPatterns("/v1/**", "/api/v1/**")
                .excludePathPatterns(
                        // 公开端点
                        "/v1/health",
                        "/v1/providers",
                        "/api/v1/auth/**",
                        "/api/v1/system/providers/**",
                        "/api/v1/multimodal/files/**",
                        "/api/v1/knowledge/diagnose/**",
                        // Agent和配置端点
                        "/api/v1/agents/**",
                        "/api/v1/model-config/**",
                        "/api/v1/conversation-logs/**",
                        // 用户权限管理端点
                        "/api/v1/departments/**",
                        "/api/v1/roles/**",
                        "/api/v1/users/**")
                .order(1);

        // 添加速率限制拦截器(在API密钥验证之后)
        registry.addInterceptor(apiRateLimitInterceptor)
                .addPathPatterns("/v1/**", "/api/v1/**")
                .excludePathPatterns(
                        // 公开端点
                        "/v1/health",
                        "/v1/providers",
                        "/api/v1/auth/**",
                        "/api/v1/system/providers/**",
                        "/api/v1/multimodal/files/**",
                        "/api/v1/knowledge/diagnose/**",
                        // Agent和配置端点
                        "/api/v1/agents/**",
                        "/api/v1/model-config/**",
                        "/api/v1/conversation-logs/**",
                        // 用户权限管理端点
                        "/api/v1/departments/**",
                        "/api/v1/roles/**",
                        "/api/v1/users/**")
                .order(2);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Map /uploads/** to the local storage/uploads directory
        String uploadPath = Paths.get("storage/uploads").toAbsolutePath().toUri().toString();

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadPath);
    }
}
