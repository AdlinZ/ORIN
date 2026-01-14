package com.adlin.orin.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT认证过滤器
 * 拦截请求，解析Header中的Token，验证并设置SecurityContext
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // 1. Check if Authorization header is present and starts with Bearer
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final String jwt = authHeader.substring(7);

            // 2. Extract user info from token
            if (jwtService.isTokenExpired(jwt)) {
                log.warn("JWT Token is expired");
                filterChain.doFilter(request, response);
                return;
            }

            final String userId = jwtService.extractUserId(jwt);
            final String username = jwtService.extractUsername(jwt);

            // 3. Check if user is already authenticated
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (userId != null && authentication == null) {
                // In a real scenario, we should loadUserDetails from DB to be safer
                // For now, we trust the token's claims or just assign default roles/authorities
                // Note: Roles should optimally be embedded in the token or fetched from DB

                // Extract roles from token if available, otherwise default to USER
                // Assuming JwtService can be updated to put/get roles
                Claims claims = jwtService.extractAllClaims(jwt);
                List<String> roles = (List<String>) claims.get("roles");

                List<GrantedAuthority> authorities;
                if (roles != null && !roles.isEmpty()) {
                    authorities = roles.stream()
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList());
                } else {
                    // Fallback to basic role if none found (caution: this might
                    // over/under-privilege)
                    // Better approach: UserDetailsService.loadByUsername(...)
                    authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
                }

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userId, // principal
                        null, // credentials
                        authorities // authorities
                );

                // Add details
                // authToken.setDetails(new
                // WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.debug("User authenticated: {}", username);
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
