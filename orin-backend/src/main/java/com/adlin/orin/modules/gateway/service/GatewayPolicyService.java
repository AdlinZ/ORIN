package com.adlin.orin.modules.gateway.service;

import com.adlin.orin.modules.gateway.dto.GatewayPolicyRequest;
import com.adlin.orin.modules.gateway.dto.GatewayPolicyResponse;
import com.adlin.orin.modules.gateway.dto.GatewayPoliciesResponse;
import com.adlin.orin.modules.gateway.entity.GatewayCircuitBreakerPolicy;
import com.adlin.orin.modules.gateway.entity.GatewayRateLimitPolicy;
import com.adlin.orin.modules.gateway.entity.GatewayRetryPolicy;
import com.adlin.orin.modules.gateway.repository.GatewayCircuitBreakerPolicyRepository;
import com.adlin.orin.modules.gateway.repository.GatewayRateLimitPolicyRepository;
import com.adlin.orin.modules.gateway.repository.GatewayRetryPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayPolicyService {

    private final GatewayRateLimitPolicyRepository rateLimitRepository;
    private final GatewayCircuitBreakerPolicyRepository circuitBreakerRepository;
    private final GatewayRetryPolicyRepository retryRepository;

    public GatewayPoliciesResponse getAllPolicies() {
        return GatewayPoliciesResponse.builder()
                .rateLimitPolicies(getRateLimitPolicies())
                .circuitBreakerPolicies(getCircuitBreakerPolicies())
                .retryPolicies(getRetryPolicies())
                .build();
    }

    public List<GatewayPolicyResponse> getRateLimitPolicies() {
        return rateLimitRepository.findAllByOrderByName().stream()
                .map(this::toRateLimitResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public GatewayPolicyResponse createRateLimitPolicy(GatewayPolicyRequest request) {
        GatewayRateLimitPolicy policy = GatewayRateLimitPolicy.builder()
                .name(request.getName())
                .dimension(request.getDimension() != null ? request.getDimension() : "GLOBAL")
                .capacity(request.getCapacity() != null ? request.getCapacity() : 100)
                .windowSeconds(request.getWindowSeconds() != null ? request.getWindowSeconds() : 60)
                .burst(request.getBurst() != null ? request.getBurst() : 10)
                .description(request.getDescription())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .build();
        policy = rateLimitRepository.save(policy);
        log.info("Created rate limit policy: {} ({})", policy.getName(), policy.getId());
        return toRateLimitResponse(policy);
    }

    @Transactional
    public GatewayPolicyResponse updateRateLimitPolicy(Long id, GatewayPolicyRequest request) {
        GatewayRateLimitPolicy policy = rateLimitRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rate limit policy not found: " + id));
        if (request.getName() != null) policy.setName(request.getName());
        if (request.getDimension() != null) policy.setDimension(request.getDimension());
        if (request.getCapacity() != null) policy.setCapacity(request.getCapacity());
        if (request.getWindowSeconds() != null) policy.setWindowSeconds(request.getWindowSeconds());
        if (request.getBurst() != null) policy.setBurst(request.getBurst());
        if (request.getDescription() != null) policy.setDescription(request.getDescription());
        if (request.getEnabled() != null) policy.setEnabled(request.getEnabled());
        policy = rateLimitRepository.save(policy);
        log.info("Updated rate limit policy: {} ({})", policy.getName(), policy.getId());
        return toRateLimitResponse(policy);
    }

    @Transactional
    public void deleteRateLimitPolicy(Long id) {
        if (!rateLimitRepository.existsById(id)) {
            throw new RuntimeException("Rate limit policy not found: " + id);
        }
        rateLimitRepository.deleteById(id);
        log.info("Deleted rate limit policy: {}", id);
    }

    public List<GatewayPolicyResponse> getCircuitBreakerPolicies() {
        return circuitBreakerRepository.findAllByOrderByName().stream()
                .map(this::toCircuitBreakerResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public GatewayPolicyResponse createCircuitBreakerPolicy(GatewayPolicyRequest request) {
        GatewayCircuitBreakerPolicy policy = GatewayCircuitBreakerPolicy.builder()
                .name(request.getName())
                .failureThreshold(request.getFailureThreshold() != null ? request.getFailureThreshold() : 5)
                .successThreshold(request.getSuccessThreshold() != null ? request.getSuccessThreshold() : 2)
                .timeoutSeconds(request.getTimeoutSeconds() != null ? request.getTimeoutSeconds() : 60)
                .halfOpenMaxRequests(request.getHalfOpenMaxRequests() != null ? request.getHalfOpenMaxRequests() : 3)
                .description(request.getDescription())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .build();
        policy = circuitBreakerRepository.save(policy);
        log.info("Created circuit breaker policy: {} ({})", policy.getName(), policy.getId());
        return toCircuitBreakerResponse(policy);
    }

    @Transactional
    public GatewayPolicyResponse updateCircuitBreakerPolicy(Long id, GatewayPolicyRequest request) {
        GatewayCircuitBreakerPolicy policy = circuitBreakerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Circuit breaker policy not found: " + id));
        if (request.getName() != null) policy.setName(request.getName());
        if (request.getFailureThreshold() != null) policy.setFailureThreshold(request.getFailureThreshold());
        if (request.getSuccessThreshold() != null) policy.setSuccessThreshold(request.getSuccessThreshold());
        if (request.getTimeoutSeconds() != null) policy.setTimeoutSeconds(request.getTimeoutSeconds());
        if (request.getHalfOpenMaxRequests() != null) policy.setHalfOpenMaxRequests(request.getHalfOpenMaxRequests());
        if (request.getDescription() != null) policy.setDescription(request.getDescription());
        if (request.getEnabled() != null) policy.setEnabled(request.getEnabled());
        policy = circuitBreakerRepository.save(policy);
        log.info("Updated circuit breaker policy: {} ({})", policy.getName(), policy.getId());
        return toCircuitBreakerResponse(policy);
    }

    @Transactional
    public void deleteCircuitBreakerPolicy(Long id) {
        if (!circuitBreakerRepository.existsById(id)) {
            throw new RuntimeException("Circuit breaker policy not found: " + id);
        }
        circuitBreakerRepository.deleteById(id);
        log.info("Deleted circuit breaker policy: {}", id);
    }

    public List<GatewayPolicyResponse> getRetryPolicies() {
        return retryRepository.findAllByOrderByName().stream()
                .map(this::toRetryResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public GatewayPolicyResponse createRetryPolicy(GatewayPolicyRequest request) {
        GatewayRetryPolicy policy = GatewayRetryPolicy.builder()
                .name(request.getName())
                .maxAttempts(request.getMaxAttempts() != null ? request.getMaxAttempts() : 3)
                .retryOnStatusCodes(request.getRetryOnStatusCodes() != null ? request.getRetryOnStatusCodes() : "500,502,503,504")
                .retryOnExceptions(request.getRetryOnExceptions())
                .backoffMultiplier(request.getBackoffMultiplier() != null ? request.getBackoffMultiplier() : 2.0)
                .initialIntervalMs(request.getInitialIntervalMs() != null ? request.getInitialIntervalMs() : 100)
                .description(request.getDescription())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .build();
        policy = retryRepository.save(policy);
        log.info("Created retry policy: {} ({})", policy.getName(), policy.getId());
        return toRetryResponse(policy);
    }

    @Transactional
    public GatewayPolicyResponse updateRetryPolicy(Long id, GatewayPolicyRequest request) {
        GatewayRetryPolicy policy = retryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Retry policy not found: " + id));
        if (request.getName() != null) policy.setName(request.getName());
        if (request.getMaxAttempts() != null) policy.setMaxAttempts(request.getMaxAttempts());
        if (request.getRetryOnStatusCodes() != null) policy.setRetryOnStatusCodes(request.getRetryOnStatusCodes());
        if (request.getRetryOnExceptions() != null) policy.setRetryOnExceptions(request.getRetryOnExceptions());
        if (request.getBackoffMultiplier() != null) policy.setBackoffMultiplier(request.getBackoffMultiplier());
        if (request.getInitialIntervalMs() != null) policy.setInitialIntervalMs(request.getInitialIntervalMs());
        if (request.getDescription() != null) policy.setDescription(request.getDescription());
        if (request.getEnabled() != null) policy.setEnabled(request.getEnabled());
        policy = retryRepository.save(policy);
        log.info("Updated retry policy: {} ({})", policy.getName(), policy.getId());
        return toRetryResponse(policy);
    }

    @Transactional
    public void deleteRetryPolicy(Long id) {
        if (!retryRepository.existsById(id)) {
            throw new RuntimeException("Retry policy not found: " + id);
        }
        retryRepository.deleteById(id);
        log.info("Deleted retry policy: {}", id);
    }

    private GatewayPolicyResponse toRateLimitResponse(GatewayRateLimitPolicy policy) {
        return GatewayPolicyResponse.builder()
                .id(policy.getId())
                .name(policy.getName())
                .type("RATE_LIMIT")
                .dimension(policy.getDimension())
                .capacity(policy.getCapacity())
                .windowSeconds(policy.getWindowSeconds())
                .burst(policy.getBurst())
                .description(policy.getDescription())
                .enabled(policy.getEnabled())
                .createdAt(policy.getCreatedAt())
                .updatedAt(policy.getUpdatedAt())
                .build();
    }

    private GatewayPolicyResponse toCircuitBreakerResponse(GatewayCircuitBreakerPolicy policy) {
        return GatewayPolicyResponse.builder()
                .id(policy.getId())
                .name(policy.getName())
                .type("CIRCUIT_BREAKER")
                .failureThreshold(policy.getFailureThreshold())
                .successThreshold(policy.getSuccessThreshold())
                .timeoutSeconds(policy.getTimeoutSeconds())
                .halfOpenMaxRequests(policy.getHalfOpenMaxRequests())
                .description(policy.getDescription())
                .enabled(policy.getEnabled())
                .createdAt(policy.getCreatedAt())
                .updatedAt(policy.getUpdatedAt())
                .build();
    }

    private GatewayPolicyResponse toRetryResponse(GatewayRetryPolicy policy) {
        return GatewayPolicyResponse.builder()
                .id(policy.getId())
                .name(policy.getName())
                .type("RETRY")
                .maxAttempts(policy.getMaxAttempts())
                .retryOnStatusCodes(policy.getRetryOnStatusCodes())
                .backoffMultiplier(policy.getBackoffMultiplier())
                .initialIntervalMs(policy.getInitialIntervalMs())
                .description(policy.getDescription())
                .enabled(policy.getEnabled())
                .createdAt(policy.getCreatedAt())
                .updatedAt(policy.getUpdatedAt())
                .build();
    }
}
