package com.adlin.orin.security;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Set;

/**
 * SSRF 防护工具类
 * 防止服务器端请求伪造攻击
 */
@Slf4j
public class SsrfProtectionUtil {

    // 禁止访问的内部 IP 范围
    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost",
            "localhost.localdomain",
            "metadata.google.internal",      // GCP metadata
            "metadata.google"               // GCP metadata alternative
    );

    // 禁止访问的 IP 地址
    private static final Set<String> BLOCKED_IPS = Set.of(
            "127.0.0.1",
            "127.0.0.2",
            "127.0.0.3",
            "127.0.0.4",
            "127.0.0.5",
            "127.0.0.6",
            "127.0.0.7",
            "127.0.0.8",
            "127.0.0.9",
            "::1",                          // IPv6 localhost
            "0.0.0.0",
            "255.255.255.255"
    );

    // 云元数据服务 IP
    private static final Set<String> METADATA_IPS = Set.of(
            "169.254.169.254",             // AWS, GCP, Azure, Alibaba Cloud
            "169.254.169.253",             // Alibaba Cloud alternative
            "169.254.169.249",             // Oracle Cloud
            "metadata.google.internal"     // GCP hostname
    );

    // 私有 IP 范围 (RFC 1918)
    private static final String[] PRIVATE_IP_RANGES = {
            "10.",                          // 10.0.0.0/8
            "172.16.",                      // 172.16.0.0/12
            "172.17.",
            "172.18.",
            "172.19.",
            "172.20.",
            "172.21.",
            "172.22.",
            "172.23.",
            "172.24.",
            "172.25.",
            "172.26.",
            "172.27.",
            "172.28.",
            "172.29.",
            "172.30.",
            "172.31.",
            "192.168.",                     // 192.168.0.0/16
            "127.",                         // Loopback
            "169.254.",                     // Link-local
            "224.",                         // Multicast
            "240."                          // Reserved
    };

    // Link-local IP 范围
    private static final String LINK_LOCAL_PREFIX = "169.254.";

    /**
     * 验证 URL 是否安全（不包含内部资源）
     *
     * @param url 要验证的 URL
     * @throws SecurityException 如果 URL 指向内部资源
     */
    public static void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new SecurityException("URL cannot be empty");
        }

        try {
            URI uri = new URI(url);
            String host = uri.getHost();

            if (host == null) {
                // 可能是 IP 地址
                host = url;
            }

            validateHost(host);
        } catch (Exception e) {
            log.warn("Failed to parse URL for SSRF check: {}", url);
            throw new SecurityException("Invalid URL format: " + url);
        }
    }

    /**
     * 验证主机名是否安全
     *
     * @param host 主机名或 IP 地址
     * @throws SecurityException 如果主机是内部资源
     */
    public static void validateHost(String host) {
        if (host == null || host.isBlank()) {
            throw new SecurityException("Host cannot be empty");
        }

        // 转换为小写进行统一比较
        String lowerHost = host.toLowerCase();

        // 1. 检查禁止的主机名
        if (BLOCKED_HOSTS.contains(lowerHost)) {
            throw new SecurityException("Access to host '" + host + "' is not allowed (blocked hostname)");
        }

        // 2. 检查是否是 IP 地址
        if (isIpAddress(host)) {
            validateIpAddress(host);
        } else {
            // 3. 如果是域名，解析并检查实际 IP
            try {
                InetAddress[] addresses = InetAddress.getAllByName(host);
                for (InetAddress address : addresses) {
                    validateInetAddress(address);
                }
            } catch (UnknownHostException e) {
                // 无法解析的域名，可能是内部域名，直接拒绝
                log.warn("Cannot resolve hostname, blocking access: {}", host);
                throw new SecurityException("Cannot resolve hostname, access denied: " + host);
            }
        }
    }

    /**
     * 验证 IP 地址是否安全
     */
    private static void validateIpAddress(String ip) {
        // 1. 检查禁止的 IP
        if (BLOCKED_IPS.contains(ip)) {
            throw new SecurityException("Access to IP '" + ip + "' is not allowed (blocked IP)");
        }

        // 2. 检查云元数据服务
        if (METADATA_IPS.contains(ip)) {
            throw new SecurityException("Access to cloud metadata service '" + ip + "' is not allowed");
        }

        // 3. 检查私有 IP 范围
        if (isPrivateIp(ip)) {
            throw new SecurityException("Access to private IP range '" + ip + "' is not allowed");
        }
    }

    /**
     * 验证 InetAddress 是否安全
     */
    private static void validateInetAddress(InetAddress address) {
        String ip = address.getHostAddress();

        // 检查是否是完全禁止的 IP
        if (BLOCKED_IPS.contains(ip)) {
            throw new SecurityException("Access to IP '" + ip + "' is not allowed");
        }

        // 检查是否是云元数据服务
        if (METADATA_IPS.contains(ip)) {
            throw new SecurityException("Access to cloud metadata service '" + ip + "' is not allowed");
        }

        // 检查是否是私有 IP
        if (address.isSiteLocalAddress() || address.isLoopbackAddress() ||
                address.isLinkLocalAddress() || address.isMulticastAddress()) {
            throw new SecurityException("Access to internal/private IP '" + ip + "' is not allowed");
        }
    }

    /**
     * 判断字符串是否是 IP 地址
     */
    private static boolean isIpAddress(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        // 简单的 IPv4 检查
        String[] parts = value.split("\\.");
        if (parts.length == 4) {
            for (String part : parts) {
                try {
                    int num = Integer.parseInt(part);
                    if (num < 0 || num > 255) {
                        return false;
                    }
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            return true;
        }

        // IPv6 检查
        if (value.contains(":")) {
            return value.contains("::") || value.matches("([0-9a-fA-F]{1,4}:){1,7}[0-9a-fA-F]{1,4}");
        }

        return false;
    }

    /**
     * 判断 IP 是否在私有范围内
     */
    private static boolean isPrivateIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }

        // 检查 link-local (169.254.x.x)
        if (ip.startsWith(LINK_LOCAL_PREFIX)) {
            return true;
        }

        // 检查私有范围前缀
        for (String prefix : PRIVATE_IP_RANGES) {
            if (ip.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }
}
