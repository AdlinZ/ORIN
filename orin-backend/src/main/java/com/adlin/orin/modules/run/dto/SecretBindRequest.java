package com.adlin.orin.modules.run.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * ADR-001/ADR-002 /secret-bind 请求（ADR-002 D-2.7）。
 */
@Data
public class SecretBindRequest {

    /** Assignment id（Control Plane 从 assignment_id 派生 run_id / lease_id / runner_id）。 */
    @NotBlank
    private String assignmentId;
}
