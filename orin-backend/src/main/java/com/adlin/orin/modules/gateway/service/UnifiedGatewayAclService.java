package com.adlin.orin.modules.gateway.service;

import com.adlin.orin.modules.gateway.dto.UnifiedGatewayAclRuleRequest;
import com.adlin.orin.modules.gateway.dto.UnifiedGatewayAclRuleResponse;
import com.adlin.orin.modules.gateway.entity.UnifiedGatewayAclRule;
import com.adlin.orin.modules.gateway.repository.UnifiedGatewayAclRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnifiedGatewayAclService {

    private final UnifiedGatewayAclRuleRepository aclRepository;

    public List<UnifiedGatewayAclRuleResponse> getAllRules() {
        return aclRepository.findAllByOrderByPriorityDesc().stream()
                .map(UnifiedGatewayAclRuleResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public UnifiedGatewayAclRuleResponse getRule(Long id) {
        UnifiedGatewayAclRule rule = aclRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ACL rule not found: " + id));
        return UnifiedGatewayAclRuleResponse.fromEntity(rule);
    }

    @Transactional
    public UnifiedGatewayAclRuleResponse createRule(UnifiedGatewayAclRuleRequest request) {
        UnifiedGatewayAclRule rule = UnifiedGatewayAclRule.builder()
                .name(request.getName())
                .type(request.getType())
                .ipPattern(request.getIpPattern())
                .pathPattern(request.getPathPattern())
                .apiKeyRequired(request.getApiKeyRequired() != null ? request.getApiKeyRequired() : false)
                .description(request.getDescription())
                .priority(request.getPriority() != null ? request.getPriority() : 0)
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .build();
        rule = aclRepository.save(rule);
        log.info("Created ACL rule: {} ({})", rule.getName(), rule.getId());
        return UnifiedGatewayAclRuleResponse.fromEntity(rule);
    }

    @Transactional
    public UnifiedGatewayAclRuleResponse updateRule(Long id, UnifiedGatewayAclRuleRequest request) {
        UnifiedGatewayAclRule rule = aclRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ACL rule not found: " + id));
        if (request.getName() != null) rule.setName(request.getName());
        if (request.getType() != null) rule.setType(request.getType());
        if (request.getIpPattern() != null) rule.setIpPattern(request.getIpPattern());
        if (request.getPathPattern() != null) rule.setPathPattern(request.getPathPattern());
        if (request.getApiKeyRequired() != null) rule.setApiKeyRequired(request.getApiKeyRequired());
        if (request.getDescription() != null) rule.setDescription(request.getDescription());
        if (request.getPriority() != null) rule.setPriority(request.getPriority());
        if (request.getEnabled() != null) rule.setEnabled(request.getEnabled());
        rule = aclRepository.save(rule);
        log.info("Updated ACL rule: {} ({})", rule.getName(), rule.getId());
        return UnifiedGatewayAclRuleResponse.fromEntity(rule);
    }

    @Transactional
    public void deleteRule(Long id) {
        if (!aclRepository.existsById(id)) {
            throw new RuntimeException("ACL rule not found: " + id);
        }
        aclRepository.deleteById(id);
        log.info("Deleted ACL rule: {}", id);
    }

    public Map<String, Object> testIp(String ip, String path) {
        List<UnifiedGatewayAclRule> rules = aclRepository.findByEnabledOrderByPriorityDesc(true);

        Map<String, Object> result = new HashMap<>();
        result.put("ip", ip);
        result.put("path", path);

        for (UnifiedGatewayAclRule rule : rules) {
            boolean ipMatches = matchesIp(rule.getIpPattern(), ip);
            boolean pathMatches = rule.getPathPattern() == null ||
                                  matchesPath(rule.getPathPattern(), path);

            if (ipMatches && pathMatches) {
                result.put("matched", true);
                result.put("ruleId", rule.getId());
                result.put("ruleName", rule.getName());
                result.put("ruleType", rule.getType());
                result.put("action", "WHITELIST".equals(rule.getType()) ? "ALLOW" : "DENY");
                result.put("apiKeyRequired", Boolean.TRUE.equals(rule.getApiKeyRequired()));
                return result;
            }
        }

        result.put("matched", false);
        result.put("action", "ALLOW");
        result.put("apiKeyRequired", false);
        return result;
    }

    public boolean isAllowed(String clientIp, String path) {
        Map<String, Object> testResult = testIp(clientIp, path);
        return "ALLOW".equals(testResult.get("action"));
    }

    private boolean matchesIp(String pattern, String ip) {
        if (pattern == null || pattern.isEmpty()) return true;
        if (pattern.contains(",")) {
            for (String p : pattern.split(",")) {
                if (matchesIp(p.trim(), ip)) return true;
            }
            return false;
        }
        if (pattern.contains("/")) {
            return isInCidr(pattern, ip);
        }
        return pattern.equals(ip);
    }

    private boolean isInCidr(String cidr, String ip) {
        try {
            String[] parts = cidr.split("/");
            String network = parts[0];
            int prefix = Integer.parseInt(parts[1]);

            long networkLong = ipToLong(network);
            long ipLong = ipToLong(ip);
            long mask = (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;

            return (networkLong & mask) == (ipLong & mask);
        } catch (Exception e) {
            return false;
        }
    }

    private long ipToLong(String ip) {
        String[] octets = ip.split("\\.");
        return (Long.parseLong(octets[0]) << 24) |
               (Long.parseLong(octets[1]) << 16) |
               (Long.parseLong(octets[2]) << 8) |
               Long.parseLong(octets[3]);
    }

    private boolean matchesPath(String pattern, String path) {
        if (pattern.equals(path)) return true;
        if (pattern.endsWith("/**")) {
            return path.startsWith(pattern.substring(0, pattern.length() - 3));
        }
        if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            int slashIdx = path.indexOf('/', prefix.length());
            return path.startsWith(prefix) && (slashIdx == -1 || slashIdx == path.length());
        }
        return false;
    }
}
