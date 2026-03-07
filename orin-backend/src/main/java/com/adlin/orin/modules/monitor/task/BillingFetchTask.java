package com.adlin.orin.modules.monitor.task;

import com.adlin.orin.modules.monitor.service.ProviderBillingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 计费数据定时拉取任务
 * 每天凌晨 2 点拉取前一天的计费数据
 */
@Slf4j
@Component
public class BillingFetchTask {

    private final ProviderBillingService billingService;

    public BillingFetchTask(ProviderBillingService billingService) {
        this.billingService = billingService;
    }

    /**
     * 每天凌晨 2 点拉取计费数据
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void fetchDailyBilling() {
        log.info("Starting daily billing fetch task...");
        try {
            billingService.fetchAllProviderBillings();
            log.info("Daily billing fetch completed.");
        } catch (Exception e) {
            log.error("Daily billing fetch failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 每小时同步一次（可选，用于实时计费）
     */
    @Scheduled(fixedRate = 3600000)
    public void fetchHourlyBilling() {
        log.debug("Starting hourly billing sync...");
        try {
            billingService.fetchAllProviderBillings();
        } catch (Exception e) {
            log.error("Hourly billing sync failed: {}", e.getMessage());
        }
    }
}
