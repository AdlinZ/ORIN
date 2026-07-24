package com.adlin.orin.modules.run.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.Map;

/**
 * ADR-001/ADR-002 /secret-bind 响应（ADR-002 D-2.7）。
 *
 * <p>MVP 阶段返回空 materialized secrets。
 */
@Data
@Builder
public class SecretBindResponse {

    /** Materialized secrets（key 为 injectAs alias）。MVP 返回空 map。 */
    @Builder.Default
    private Map<String, String> materializedSecrets = Collections.emptyMap();

    public static SecretBindResponse empty() {
        return SecretBindResponse.builder().build();
    }
}
