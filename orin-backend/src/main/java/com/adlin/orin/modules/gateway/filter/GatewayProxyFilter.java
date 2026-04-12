package com.adlin.orin.modules.gateway.filter;

import com.adlin.orin.modules.gateway.entity.GatewayRoute;
import com.adlin.orin.modules.gateway.repository.GatewayRouteRepository;
import com.adlin.orin.modules.gateway.service.GatewayAclService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;

/**
 * 网关请求转发过滤器
 * 根据路由配置将请求转发到目标服务
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class GatewayProxyFilter implements Filter {

    private final GatewayRouteRepository routeRepository;
    private final GatewayAclService aclService;
    private final RestTemplate restTemplate;

    private static final String ATTR_ROUTE = "gateway.route";
    private static final String ATTR_TARGET_URL = "gateway.targetUrl";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String path = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();

        log.debug("Gateway processing: {} {}", method, path);

        // 查找匹配的路由
        List<GatewayRoute> routes = routeRepository.findActiveRoutesOrderByPriority();
        GatewayRoute matchedRoute = routes.stream()
                .filter(r -> matchesPath(r.getPathPattern(), path))
                .filter(r -> matchesMethod(r.getMethod(), method))
                .findFirst()
                .orElse(null);

        if (matchedRoute == null) {
            // 没有匹配的路由，继续下游
            chain.doFilter(request, response);
            return;
        }

        // ACL 检查
        String clientIp = httpRequest.getRemoteAddr();
        if (!aclService.isAllowed(clientIp, path)) {
            log.warn("ACL denied: {} {}", method, path);
            httpResponse.setStatus(HttpStatus.FORBIDDEN.value());
            return;
        }

        // 构建目标URL
        String targetUrl = buildTargetUrl(matchedRoute, path);
        log.info("Proxy to: {} {}", method, targetUrl);

        // 存储到request属性
        httpRequest.setAttribute(ATTR_ROUTE, matchedRoute);
        httpRequest.setAttribute(ATTR_TARGET_URL, targetUrl);

        // 调用目标服务
        try {
            int timeout = matchedRoute.getTimeoutMs() != null ? matchedRoute.getTimeoutMs() : 30000;
            
            org.springframework.http.HttpMethod httpMethod = HttpMethod.valueOf(method);
            
            org.springframework.http.HttpEntity<?> entity = new org.springframework.http.HttpEntity<>(
                    httpRequest.getInputStream() != null ? httpRequest.getInputStream() : new byte[0],
                    getHeaders(httpRequest)
            );

            org.springframework.http.ResponseEntity<byte[]> result = restTemplate.exchange(
                    targetUrl,
                    httpMethod,
                    entity,
                    byte[].class
            );

            // 处理响应
            httpResponse.setStatus(result.getStatusCode().value());
            result.getHeaders().forEach((name, values) -> {
                if (!isHopByHopHeader(name)) {
                    values.forEach(v -> httpResponse.addHeader(name, v));
                }
            });

            if (result.getBody() != null) {
                httpResponse.getOutputStream().write(result.getBody());
            }

        } catch (Exception e) {
            log.error("Proxy failed: {} - {}", targetUrl, e.getMessage());
            httpResponse.setStatus(HttpStatus.BAD_GATEWAY.value());
        }
    }

    /**
     * 检查路径是否匹配
     */
    private boolean matchesPath(String pattern, String path) {
        if (pattern == null || path == null) return false;
        
        if (pattern.contains("*")) {
            String regex = pattern.replace("**", ".*").replace("*", "[^/]*");
            return path.matches(regex);
        }
        
        return pattern.endsWith("/**") 
            ? path.startsWith(pattern.substring(0, pattern.length() - 3))
            : path.equals(pattern);
    }

    /**
     * 检查方法是否匹配
     */
    private boolean matchesMethod(String routeMethod, String requestMethod) {
        if (routeMethod == null || routeMethod.isEmpty()) return true;
        if ("ALL".equalsIgnoreCase(routeMethod)) return true;
        return routeMethod.equalsIgnoreCase(requestMethod);
    }

    /**
     * 构建目标URL
     */
    private String buildTargetUrl(GatewayRoute route, String requestPath) {
        String targetUrl = route.getTargetUrl();
        if (targetUrl == null || targetUrl.isEmpty()) {
            throw new IllegalStateException("Target URL not configured for route: " + route.getName());
        }

        String targetPath = requestPath;
        
        // stripPrefix
        if (Boolean.TRUE.equals(route.getStripPrefix())) {
            String pattern = route.getPathPattern();
            if (pattern.endsWith("/**")) {
                pattern = pattern.substring(0, pattern.length() - 3);
            }
            if (requestPath.startsWith(pattern)) {
                targetPath = requestPath.substring(pattern.length());
            }
            if (targetPath.isEmpty()) targetPath = "/";
        }

        // rewritePath
        if (route.getRewritePath() != null && !route.getRewritePath().isEmpty()) {
            targetPath = route.getRewritePath();
        }

        // 拼接
        if (!targetUrl.endsWith("/") && !targetPath.startsWith("/")) {
            targetUrl += "/";
        }
        
        return targetUrl + targetPath;
    }

    /**
     * 获取请求头
     */
    private org.springframework.http.HttpHeaders getHeaders(HttpServletRequest request) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        
        request.getHeaderNames().asIterator().forEachRemaining(name -> {
            if (!isHopByHopHeader(name)) {
                headers.add(name, request.getHeader(name));
            }
        });
        
        headers.add("X-Forwarded-For", request.getRemoteAddr());
        headers.add("X-Original-URI", request.getRequestURI());
        
        GatewayRoute route = (GatewayRoute) request.getAttribute(ATTR_ROUTE);
        if (route != null) {
            headers.add("X-Gateway-Route", route.getName());
        }
        
        return headers;
    }

    /**
     * 是否是Hop-by-Hop Header
     */
    private boolean isHopByHopHeader(String name) {
        return "connection".equalsIgnoreCase(name)
                || "keep-alive".equalsIgnoreCase(name)
                || "proxy-authenticate".equalsIgnoreCase(name)
                || "proxy-authorization".equalsIgnoreCase(name)
                || "te".equalsIgnoreCase(name)
                || "trailers".equalsIgnoreCase(name)
                || "transfer-encoding".equalsIgnoreCase(name)
                || "upgrade".equalsIgnoreCase(name);
    }
}