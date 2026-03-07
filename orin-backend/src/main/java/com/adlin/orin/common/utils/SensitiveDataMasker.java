package com.adlin.orin.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 敏感数据脱敏工具
 * 用于日志、审计等场景下对敏感信息进行脱敏处理
 */
@Slf4j
@Component
public class SensitiveDataMasker {

    // 敏感字段名称模式
    private static final Pattern SENSITIVE_FIELD_PATTERN = Pattern.compile(
            "(?i)(password|passwd|pwd|secret|token|api[_-]?key|apikey|access[_-]?key|private[_-]?key|auth[_-]?key|bearer|authorization|credential|secret[_-]?key)",
            Pattern.CASE_INSENSITIVE
    );

    // 敏感值模式 (API Key, Token等)
    private static final Pattern SENSITIVE_VALUE_PATTERN = Pattern.compile(
            "(?i)(sk-|ak-|Bearer\\s+[a-zA-Z0-9\\-\\._~+/]+=*|ghp_[a-zA-Z0-9]{36}|gho_[a-zA-Z0-9]{36}|glpat-[a-zA-Z0-9\\-_]{20})"
    );

    // 邮箱模式
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
    );

    // 手机号模式 (中国)
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?i)(1[3-9]\\d{9})"
    );

    /**
     * 脱敏敏感字段
     * @param data 待处理的数据
     * @return 脱敏后的数据
     */
    public String mask(String data) {
        if (data == null || data.isEmpty()) {
            return data;
        }

        String masked = data;

        // 脱敏 API Key/Token 格式的值
        masked = SENSITIVE_VALUE_PATTERN.matcher(masked).replaceAll("***MASKED***");

        // 脱敏邮箱
        masked = EMAIL_PATTERN.matcher(masked).replaceAll("***EMAIL***");

        // 脱敏手机号
        masked = PHONE_PATTERN.matcher(masked).replaceAll("***PHONE***");

        return masked;
    }

    /**
     * 脱敏对象中的敏感字段
     * @param data 待处理的数据
     * @return 脱敏后的数据
     */
    public String maskObject(String data) {
        if (data == null || data.isEmpty()) {
            return data;
        }

        String result = data;

        // 查找 key: value 格式并脱敏值
        String[] lines = data.split("\n");
        StringBuilder sb = new StringBuilder();

        for (String line : lines) {
            if (SENSITIVE_FIELD_PATTERN.matcher(line).find()) {
                // 找到敏感字段，脱敏整个值
                line = SENSITIVE_VALUE_PATTERN.matcher(line).replaceAll("***MASKED***");
            }
            sb.append(line).append("\n");
        }

        result = sb.toString();
        return mask(result); // 再进行通用脱敏
    }

    /**
     * 脱敏请求参数
     * @param params 请求参数
     * @return 脱敏后的参数
     */
    public String maskRequestParams(String params) {
        if (params == null) {
            return null;
        }
        return maskObject(params);
    }

    /**
     * 脱敏响应内容
     * @param response 响应内容
     * @return 脱敏后的响应
     */
    public String maskResponse(String response) {
        if (response == null) {
            return null;
        }
        return mask(response);
    }

    /**
     * 判断是否为敏感字段
     * @param fieldName 字段名
     * @return 是否敏感
     */
    public boolean isSensitiveField(String fieldName) {
        return SENSITIVE_FIELD_PATTERN.matcher(fieldName).find();
    }
}
