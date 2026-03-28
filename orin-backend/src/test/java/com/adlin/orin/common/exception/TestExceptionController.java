package com.adlin.orin.common.exception;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试专用控制器 - 用于 GlobalExceptionHandler 的异常场景测试
 * 所有端点仅在测试环境使用，不会出现在生产代码中
 */
@RestController
class TestExceptionController {

    @GetMapping("/test/business-exception")
    public void throwBusinessException() {
        throw new BusinessException(ErrorCode.AGENT_NOT_FOUND);
    }

    @GetMapping("/test/resource-not-found")
    public void throwResourceNotFound() {
        throw new ResourceNotFoundException("Agent", "non-existent-id");
    }

    @GetMapping("/test/validation-exception")
    public void throwValidationException() {
        throw new ValidationException("字段验证失败");
    }

    @GetMapping("/test/authentication-exception")
    public void throwAuthenticationException() {
        throw new AuthenticationException("认证失败");
    }

    @GetMapping("/test/authorization-exception")
    public void throwAuthorizationException() {
        throw new AuthorizationException("无权限访问");
    }

    @GetMapping("/test/uncaught-exception")
    public void throwUncaughtException() {
        throw new RuntimeException("未捕获的原始异常");
    }
}
