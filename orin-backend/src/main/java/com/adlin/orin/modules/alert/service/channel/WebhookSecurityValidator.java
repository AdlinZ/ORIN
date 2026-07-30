package com.adlin.orin.modules.alert.service.channel;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * Webhook URL 安全校验，防止 SSRF。
 */
@Component
public class WebhookSecurityValidator {

    public void validate(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Webhook URL 不能为空");
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Webhook URL 格式无效: " + url);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Webhook URL 必须使用 HTTPS 协议");
        }
        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("Webhook URL 缺少合法 Host");
        }
        if (Pattern.matches(
            "(localhost|.*\\.local|127\\..+|10\\..+|172\\.(1[6-9]|2[0-9]|3[01])\\..+|192\\.168\\..+|0\\.0\\.0\\.0|169\\.254\\..+|::1|fc.+|fd.+)",
            host.toLowerCase())) {
            throw new IllegalArgumentException("Webhook URL 不允许指向内网地址");
        }
    }
}
