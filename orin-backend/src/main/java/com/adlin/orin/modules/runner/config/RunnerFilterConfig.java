package com.adlin.orin.modules.runner.config;

import com.adlin.orin.modules.runner.service.RunnerCredentialService;
import com.adlin.orin.modules.runner.service.RunnerService;
import com.adlin.orin.security.EnrollmentTokenAuthFilter;
import com.adlin.orin.security.RunnerCredentialAuthFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Runner 机器通道鉴权 filter 的 bean 装配。
 *
 * <p>独立于 {@code SecurityConfig} 以避免现有 security 单元测试在 SecurityConfig 引入新依赖
 * 后被强制提供 RunnerService / RunnerCredentialService / ObjectMapper。Spring 在装配
 * SecurityFilterChain 时会通过容器按名找到这里定义的 filter bean。
 */
@Configuration
public class RunnerFilterConfig {

    @Bean
    public EnrollmentTokenAuthFilter enrollmentTokenAuthFilter(RunnerService runnerService,
                                                              ObjectMapper objectMapper) {
        return new EnrollmentTokenAuthFilter(runnerService, objectMapper);
    }

    @Bean
    public RunnerCredentialAuthFilter runnerCredentialAuthFilter(RunnerCredentialService runnerCredentialService,
                                                                ObjectMapper objectMapper) {
        return new RunnerCredentialAuthFilter(runnerCredentialService, objectMapper);
    }
}
