package com.adlin.orin.modules.runner.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建 Enrollment Token 请求。
 *
 * <p>Operator 在 UI 表单中提交：name（必填，未来 Runner 接入必须同名）+ ttlMinutes（可选）。
 * 后端校验 name 唯一（per owner）+ ttl 范围，并把默认 ttl 套用到省略场景。
 */
public record EnrollmentTokenRequest(
        @NotBlank @Size(max = 120) String name,
        @Min(0) Long ttlMinutes) {
}
