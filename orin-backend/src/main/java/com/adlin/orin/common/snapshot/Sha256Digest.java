package com.adlin.orin.common.snapshot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 hex (lowercase, 64 chars) 助手。
 *
 * <p>封装仓库现有 {@code MessageDigest.getInstance("SHA-256")} 用法（参见
 * {@code DualFileStorageService} / {@code AiEngineConfig} / {@code RestConfig}），
 * 统一输出 lowercase hex {@code char[64]}，供 AgentVersion FROZEN digest 使用。
 */
public final class Sha256Digest {

    private Sha256Digest() {
    }

    /** UTF-8 编码后求 SHA-256，返回 64-char lowercase hex。 */
    public static String hex(String input) {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        return hex(input.getBytes(StandardCharsets.UTF_8));
    }

    /** 直接对 bytes 求 SHA-256，返回 64-char lowercase hex。 */
    public static String hex(byte[] input) {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] raw = md.digest(input);
            return toHex(raw);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必带算法；抛 RuntimeException 提示环境异常
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String toHex(byte[] raw) {
        char[] hex = new char[raw.length * 2];
        for (int i = 0; i < raw.length; i++) {
            int v = raw[i] & 0xFF;
            hex[i * 2] = HEX_CHARS[v >>> 4];
            hex[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hex);
    }

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();
}
