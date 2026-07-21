package com.adlin.orin.security;

import com.adlin.orin.common.dto.Result;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.runner.entity.RunnerCredential;
import com.adlin.orin.modules.runner.service.RunnerCredentialService;
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
import java.util.Optional;

/**
 * Runner 机器通道鉴权过滤器 — 处理 {@code Authorization: Runner <credential>}。
 *
 * <p>行为（[ADR-001 §D-1.7]）：
 * <ul>
 *   <li>缺失 / 格式错 / 凭据不存在 → 401 {@code RUNNER_CREDENTIAL_INVALID}</li>
 *   <li>凭据有效但 {@code RunnerCredential.status=REVOKED} 或对应 Runner 已被撤销 → 403
 *       {@code RUNNER_REVOKED}</li>
 *   <li>成功 → 写 SecurityContext，principal 为 {@code RunnerPrincipal}，单角色 {@code ROLE_RUNNER}</li>
 * </ul>
 *
 * <p>enroll 端点由 {@code EnrollmentTokenAuthFilter} 单独接管（不同 Authorization 头）。
 */
@Slf4j
@RequiredArgsConstructor
public class RunnerCredentialAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "Authorization";
    public static final String PREFIX = "Runner ";
    public static final String RUNNER_AUTHORITY = "ROLE_RUNNER";

    private final RunnerCredentialService runnerCredentialService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // 仅处理机器通道业务端点；enroll 端点交给 EnrollmentTokenAuthFilter。
        if (path == null || !path.startsWith("/api/system/runners/")) {
            return true;
        }
        return path.equals("/api/system/runners/enroll");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {
        String auth = request.getHeader(HEADER);
        if (auth == null || !auth.startsWith(PREFIX)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    ErrorCode.RUNNER_CREDENTIAL_INVALID, "Runner Credential 缺失或格式错误");
            return;
        }
        String credential = auth.substring(PREFIX.length()).trim();
        Optional<RunnerCredential> matchOpt = runnerCredentialService.validateCredential(credential);
        if (matchOpt.isEmpty()) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    ErrorCode.RUNNER_CREDENTIAL_INVALID, "Runner Credential 无效");
            return;
        }
        RunnerCredential credentialRow = matchOpt.get();
        if (credentialRow.getStatus() != RunnerCredential.Status.ACTIVE) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN,
                    ErrorCode.RUNNER_REVOKED, "Runner 凭据已被撤销");
            return;
        }

        RunnerPrincipal principal = new RunnerPrincipal(
                credentialRow.getRunnerId(), credentialRow.getCredentialId());
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(RUNNER_AUTHORITY)));
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
