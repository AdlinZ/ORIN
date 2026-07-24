package com.adlin.orin.modules.run.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RenewLease 请求体（ADR-001 D-1.2 /renew）。
 *
 * <p>Runner 提交 lease_id；Control Plane 从 run_assignment 中派生并校验 run_id / runner_id。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RenewLeaseRequest {

    @NotBlank
    private String leaseId;
}
