package com.adlin.orin.modules.run.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * ADR-001 /result 请求 — Runner 提交最终执行结果。
 *
 * <p>合并旧 completeRun + failRun 为单一端点（ADR-001 D-1.2）。
 */
@Data
public class SubmitResultRequest {

    /** Lease 验证令牌。 */
    @NotBlank
    private String leaseToken;

    /** 执行结果：COMPLETED 或 FAILED。 */
    @NotBlank
    private String status;

    /** 成功时的输出。 */
    private String output;

    /** 失败时的错误信息。 */
    private String errorMessage;

    /** 失败时的错误码（可选）。 */
    private String errorCode;

    public boolean isSuccess() {
        return "COMPLETED".equalsIgnoreCase(status);
    }
}
