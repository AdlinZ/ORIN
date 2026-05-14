package com.adlin.orin.modules.skill.util;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MCP 服务 env 变量的密钥引用语法工具。
 *
 * <p>引用语法 {@code ${secret:<secretId>}} 必须是整个值（不支持嵌入/插值）。
 * 敏感 key（{@code *_KEY/_TOKEN/_SECRET}）只允许用引用形式，明文会被后端拒绝。
 */
public final class McpEnvSecretRef {

    /** 敏感 env key 后缀，后端各处共用这一份定义。 */
    public static final List<String> SENSITIVE_ENV_SUFFIXES = List.of("_KEY", "_TOKEN", "_SECRET");

    private static final Pattern SECRET_REF = Pattern.compile("^\\$\\{secret:([A-Za-z0-9_\\-]+)\\}$");

    private McpEnvSecretRef() {
    }

    /** value 是否是合法的 {@code ${secret:<secretId>}} 引用。 */
    public static boolean isSecretRef(String value) {
        return value != null && SECRET_REF.matcher(value.trim()).matches();
    }

    /** 从引用中取出 secretId；非引用返回 null。 */
    public static String extractSecretId(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = SECRET_REF.matcher(value.trim());
        return matcher.matches() ? matcher.group(1) : null;
    }

    /** key 是否是敏感字段（按后缀判断，大小写不敏感）。 */
    public static boolean isSensitiveKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String upper = key.trim().toUpperCase(Locale.ROOT);
        return SENSITIVE_ENV_SUFFIXES.stream().anyMatch(upper::endsWith);
    }
}
