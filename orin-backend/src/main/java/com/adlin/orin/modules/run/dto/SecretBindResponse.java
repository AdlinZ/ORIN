package com.adlin.orin.modules.run.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.Map;

/**
 * ADR-001/ADR-002 /secret-bind 响应（ADR-002 D-2.7）。
 *
 * <p>R2：物化 CONTROL_PLANE secrets 为明文，供 Runner 在进程内存中使用。
 */
@Data
@Builder
public class SecretBindResponse {

    /** 当前 lease 标识。 */
    private String leaseId;

    /** Run id。 */
    private String runId;

    /** Materialized secrets（key 为 injectAs alias，value 为解密的明文 secret）。 */
    @Builder.Default
    private Map<String, String> materializedSecrets = Collections.emptyMap();

    /** Secret revision binding（key 为 injectAs，value 为 "secret_id@revision"）。 */
    @Builder.Default
    private Map<String, String> secretRevisionBindings = Collections.emptyMap();

    /** secret 过期时间（epoch millis）。绑定 lease 过期时间。 */
    private Long expiresAtEpochMs;

    public static SecretBindResponse empty() {
        return SecretBindResponse.builder().build();
    }
}
