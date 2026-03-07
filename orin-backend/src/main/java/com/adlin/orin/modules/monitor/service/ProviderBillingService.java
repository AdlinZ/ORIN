package com.adlin.orin.modules.monitor.service;

import com.adlin.orin.modules.monitor.entity.BillingRecord;
import com.adlin.orin.modules.monitor.repository.BillingRecordRepository;
import com.adlin.orin.modules.provider.entity.ApiProvider;
import com.adlin.orin.modules.provider.repository.ApiProviderRepository;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 第三方提供商计费接口服务
 * 主动拉取各提供商的计费数据
 */
@Slf4j
@Service
public class ProviderBillingService {

    private final ApiProviderRepository providerRepository;
    private final BillingRecordRepository billingRecordRepository;
    private final RestTemplate restTemplate;

    @Value("${orin.billing.enabled:true}")
    private boolean billingEnabled;

    public ProviderBillingService(ApiProviderRepository providerRepository,
                                   BillingRecordRepository billingRecordRepository) {
        this.providerRepository = providerRepository;
        this.billingRecordRepository = billingRecordRepository;
        this.restTemplate = new RestTemplate();
    }

    /**
     * 拉取所有提供商的计费数据
     */
    public void fetchAllProviderBillings() {
        if (!billingEnabled) {
            log.debug("Billing fetch is disabled");
            return;
        }

        List<ApiProvider> providers = providerRepository.findAll();
        for (ApiProvider provider : providers) {
            try {
                fetchProviderBilling(provider);
            } catch (Exception e) {
                log.error("Failed to fetch billing for provider {}: {}", provider.getName(), e.getMessage());
            }
        }
    }

    /**
     * 拉取单个提供商的计费数据
     */
    public void fetchProviderBilling(ApiProvider provider) {
        if (provider == null || !provider.getEnabled()) {
            return;
        }

        String providerType = provider.getProviderType();
        String apiKey = provider.getApiKey();

        if (apiKey == null || apiKey.isBlank()) {
            log.debug("No API key for provider {}", provider.getName());
            return;
        }

        BillingResult result = null;

        switch (providerType.toLowerCase()) {
            case "openai":
                result = fetchOpenAIBilling(apiKey);
                break;
            case "anthropic":
                result = fetchAnthropicBilling(apiKey);
                break;
            case "azure":
                result = fetchAzureBilling(apiKey, provider.getBaseUrl());
                break;
            case "siliconflow":
                result = fetchSiliconFlowBilling(apiKey);
                break;
            case "dify":
                result = fetchDifyBilling(apiKey, provider.getBaseUrl());
                break;
            case "ollama":
            case "local":
                // 本地模型不计费
                return;
            default:
                log.debug("Unsupported billing provider type: {}", providerType);
        }

        if (result != null) {
            saveBillingRecord(provider, result);
        }
    }

    /**
     * OpenAI 计费 API
     * GET https://api.openai.com/v1/usage?date=2026-03-08
     */
    private BillingResult fetchOpenAIBilling(String apiKey) {
        try {
            String date = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_DATE);
            String url = "https://api.openai.com/v1/usage?date=" + date;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map body = response.getBody();
                List<Map> usageList = (List<Map>) body.get("data");

                if (usageList != null && !usageList.isEmpty()) {
                    // 取最新的记录
                    Map latest = usageList.get(usageList.size() - 1);
                    BigDecimal cost = new BigDecimal(latest.getOrDefault("cost", "0").toString());
                    int promptTokens = ((Number) latest.getOrDefault("prompt_tokens", 0)).intValue();
                    int completionTokens = ((Number) latest.getOrDefault("completion_tokens", 0)).intValue();

                    return BillingResult.builder()
                            .totalCost(cost)
                            .promptTokens(promptTokens)
                            .completionTokens(completionTokens)
                            .currency("USD")
                            .build();
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch OpenAI billing: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Anthropic 计费 API
     * 需要通过 API Console 或自行记录
     */
    private BillingResult fetchAnthropicBilling(String apiKey) {
        // Anthropic 目前没有公开的计费 API
        // 需要通过 account API 获取
        try {
            String url = "https://api.anthropic.com/v1/billing_usage?start_date="
                    + LocalDate.now().minusDays(30).format(DateTimeFormatter.ISO_DATE)
                    + "&end_date=" + LocalDate.now().format(DateTimeFormatter.ISO_DATE);

            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");
            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map body = response.getBody();
                Map usage = (Map) body.get("usage");
                if (usage != null) {
                    BigDecimal cost = new BigDecimal(usage.getOrDefault("cost", "0").toString());
                    return BillingResult.builder()
                            .totalCost(cost)
                            .currency("USD")
                            .build();
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch Anthropic billing: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Azure OpenAI 计费
     * 需要通过 Azure Portal API 或资源管理器获取
     */
    private BillingResult fetchAzureBilling(String apiKey, String baseUrl) {
        // Azure 计费需要通过 Azure Resource Manager API
        // 这里简化处理，实际需要 tenant_id, subscription_id 等
        log.debug("Azure billing fetch not fully implemented");
        return null;
    }

    /**
     * 硅基流动计费 API
     */
    private BillingResult fetchSiliconFlowBilling(String apiKey) {
        try {
            String url = "https://api.siliconflow.cn/v1/user/info";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map data = (Map) response.getBody().get("data");
                if (data != null) {
                    BigDecimal balance = new BigDecimal(data.getOrDefault("balance", "0").toString());
                    return BillingResult.builder()
                            .totalCost(balance.negate()) // 余额转为负成本
                            .currency("CNY")
                            .build();
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch SiliconFlow billing: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Dify 计费 API
     */
    private BillingResult fetchDifyBilling(String apiKey, String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        try {
            String url = baseUrl.trim().replaceAll("/+$", "") + "/v1/usage";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map data = response.getBody();
                int totalTokens = ((Number) data.getOrDefault("total_tokens", 0)).intValue();
                int promptTokens = ((Number) data.getOrDefault("prompt_tokens", 0)).intValue();
                int completionTokens = ((Number) data.getOrDefault("completion_tokens", 0)).intValue();

                return BillingResult.builder()
                        .totalTokens(totalTokens)
                        .promptTokens(promptTokens)
                        .completionTokens(completionTokens)
                        .currency("CNY")
                        .build();
            }
        } catch (Exception e) {
            log.error("Failed to fetch Dify billing: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 保存计费记录
     */
    private void saveBillingRecord(ApiProvider provider, BillingResult result) {
        if (result == null) {
            return;
        }

        // 检查今日是否已有记录
        LocalDate today = LocalDate.now();
        Optional<BillingRecord> existing = billingRecordRepository
                .findByProviderIdAndBillingDate(provider.getId(), today);

        if (existing.isPresent()) {
            // 更新现有记录
            BillingRecord record = existing.get();
            record.setTotalCost(result.getTotalCost());
            record.setPromptTokens(result.getPromptTokens());
            record.setCompletionTokens(result.getCompletionTokens());
            billingRecordRepository.save(record);
            log.info("Updated billing record for provider {} on {}", provider.getName(), today);
        } else {
            // 创建新记录
            BillingRecord record = new BillingRecord();
            record.setProviderId(provider.getId());
            record.setProviderName(provider.getName());
            record.setBillingDate(today);
            record.setTotalCost(result.getTotalCost());
            record.setPromptTokens(result.getPromptTokens());
            record.setCompletionTokens(result.getCompletionTokens());
            record.setCurrency(result.getCurrency());
            billingRecordRepository.save(record);
            log.info("Created billing record for provider {} on {}", provider.getName(), today);
        }
    }

    @Data
    public static class BillingResult {
        private BigDecimal totalCost;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
        private String currency;

        public static BillingResult builder() {
            return new BillingResult();
        }
    }
}
