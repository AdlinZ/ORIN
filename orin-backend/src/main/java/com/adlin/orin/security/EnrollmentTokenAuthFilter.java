package com.adlin.orin.security;

import com.adlin.orin.common.dto.Result;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.runner.service.RunnerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Enrollment Token 鉴权过滤器 — 拦截 {@code POST /api/system/runners/enroll}，
 * 解析 {@code Authorization: Enrollment <token>} 头。
 *
 * <p>enroll 是 Runner 接入的一次性入口：调用方是尚未拥有 {@code RunnerCredential} 的新机器，
 * 必须用短时 Token 验明身份。Filter 只做非破坏性校验并保留本次请求内的 credentials；
 * Controller 进入业务服务后才在单个事务里消费 Token、创建 Runner 并签发 Credential。
 */
@Slf4j
@RequiredArgsConstructor
public class EnrollmentTokenAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "Authorization";
    public static final String PREFIX = "Enrollment ";
    public static final String ENROLLMENT_AUTHORITY = "ROLE_ENROLLMENT_TOKEN";
    public static final String ENROLL_PATH = "/api/system/runners/enroll";

    private final RunnerService runnerService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !ENROLL_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {
        String auth = request.getHeader(HEADER);
        if (auth == null || !auth.startsWith(PREFIX)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    ErrorCode.ENROLLMENT_TOKEN_INVALID, "Enrollment Token 缺失或格式错误");
            return;
        }
        String token = auth.substring(PREFIX.length()).trim();
        RunnerService.ValidatedEnrollmentToken validated;
        try {
            validated = runnerService.validateEnrollmentToken(token);
        } catch (com.adlin.orin.common.exception.BusinessException ex) {
            ErrorCode code = ex.getErrorCode();
            int status = code == ErrorCode.ENROLLMENT_TOKEN_EXPIRED
                    ? HttpServletResponse.SC_GONE
                    : org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY.value();
            writeError(response, status, code, ex.getMessage());
            return;
        } catch (Exception ex) {
            log.warn("Unexpected error during enrollment token validation: {}", ex.getMessage());
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    ErrorCode.ENROLLMENT_TOKEN_INVALID, "Enrollment Token 校验失败");
            return;
        }

        EnrollmentTokenPrincipal principal = new EnrollmentTokenPrincipal(
                validated.tokenId(), validated.expectedName(), validated.createdBy());
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                principal, token, List.of(new SimpleGrantedAuthority(ENROLLMENT_AUTHORITY)));
        SecurityContextHolder.getContext().setAuthentication(authToken);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void writeError(HttpServletResponse response, int status, ErrorCode code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        String traceId = org.slf4j.MDC.get("traceId");
        Result<Object> body = Result.<Object>builder()
                .code(code.getCode())
                .message(message)
                .traceId(traceId)
                .build();
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
