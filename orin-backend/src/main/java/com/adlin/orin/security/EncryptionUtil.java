package com.adlin.orin.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 敏感数据加密工具类
 * 用于加密存储在数据库中的 Token、密钥等敏感信息
 */
@Slf4j
@Component
public class EncryptionUtil {

    private static final String ALGORITHM = "AES";

    @Value("${encryption.key:${ENCRYPTION_KEY:}}")
    private String encryptionKey;

    /**
     * 加密字符串
     *
     * @param plainText 要加密的明文
     * @return 加密后的 Base64 编码字符串
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return plainText;
        }

        if (encryptionKey == null || encryptionKey.isBlank()) {
            log.warn("Encryption key not configured, returning plain text");
            return plainText;
        }

        try {
            SecretKeySpec key = new SecretKeySpec(getKey(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            log.error("Encryption failed: {}", e.getMessage());
            throw new RuntimeException("Failed to encrypt data", e);
        }
    }

    /**
     * 解密字符串
     *
     * @param encryptedText 加密后的 Base64 编码字符串
     * @return 解密后的明文
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isBlank()) {
            return encryptedText;
        }

        if (encryptionKey == null || encryptionKey.isBlank()) {
            log.warn("Encryption key not configured, returning encrypted text as-is");
            return encryptedText;
        }

        try {
            SecretKeySpec key = new SecretKeySpec(getKey(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 解密失败可能是数据未加密或密钥错误
            log.warn("Decryption failed (data may not be encrypted): {}", e.getMessage());
            return encryptedText;
        }
    }

    /**
     * 生成 AES 密钥 (128位 = 16字节)
     * 如果配置的密钥长度不足 16 字节，则进行填充或截断
     */
    private byte[] getKey() {
        byte[] key = new byte[16];
        byte[] source = encryptionKey.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(source, 0, key, 0, Math.min(source.length, 16));
        return key;
    }

    /**
     * 检查是否启用了加密
     */
    public boolean isEncryptionEnabled() {
        return encryptionKey != null && !encryptionKey.isBlank();
    }
}
