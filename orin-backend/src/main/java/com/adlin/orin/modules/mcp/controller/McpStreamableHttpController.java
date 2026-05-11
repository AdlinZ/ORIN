package com.adlin.orin.modules.mcp.controller;

import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import com.adlin.orin.modules.mcp.service.McpJsonRpcService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class McpStreamableHttpController {
    private final McpJsonRpcService jsonRpcService;

    @Value("${orin.mcp.allowed-origins:}")
    private String allowedOrigins;

    @PostMapping("/v1/mcp")
    public ResponseEntity<?> post(@RequestBody Object body,
                                  @RequestHeader(value = "Origin", required = false) String origin,
                                  HttpServletRequest request) {
        if (!originAllowed(origin)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Origin not allowed"));
        Object apiKey = request.getAttribute("apiKey");
        if (!(apiKey instanceof GatewaySecret secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Missing API key"));
        }
        Map<String, Object> response = jsonRpcService.handle(body, secret);
        return response == null ? ResponseEntity.accepted().build() : ResponseEntity.ok(response);
    }

    @GetMapping("/v1/mcp")
    public ResponseEntity<?> get(@RequestHeader(value = "Origin", required = false) String origin) {
        if (!originAllowed(origin)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Origin not allowed"));
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    private boolean originAllowed(String origin) {
        if (origin == null || origin.isBlank()) return true;
        Set<String> allowed = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank() && !"*".equals(s))
                .collect(Collectors.toSet());
        return !allowed.isEmpty() && allowed.contains(origin);
    }
}
