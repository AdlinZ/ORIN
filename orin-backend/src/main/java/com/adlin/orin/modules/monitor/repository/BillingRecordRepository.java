package com.adlin.orin.modules.monitor.repository;

import com.adlin.orin.modules.monitor.entity.BillingRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillingRecordRepository extends JpaRepository<BillingRecord, Long> {

    /**
     * 查询指定提供商在指定日期的计费记录
     */
    Optional<BillingRecord> findByProviderIdAndBillingDate(String providerId, LocalDate billingDate);

    /**
     * 查询指定提供商在日期范围内的计费记录
     */
    List<BillingRecord> findByProviderIdAndBillingDateBetween(String providerId, LocalDate startDate, LocalDate endDate);

    /**
     * 查询所有提供商在指定日期的计费记录
     */
    List<BillingRecord> findByBillingDate(LocalDate billingDate);

    /**
     * 查询指定日期范围的计费记录
     */
    List<BillingRecord> findByBillingDateBetween(LocalDate startDate, LocalDate endDate);
}
