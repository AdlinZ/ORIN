package com.adlin.orin.modules.model.service;

public class SiliconFlowUrlUtils {
    public static String buildUrl(String endpointUrl, String path) {
        String trimmedUrl = endpointUrl != null ? endpointUrl.trim() : "";
        if (trimmedUrl.endsWith("/")) {
            trimmedUrl = trimmedUrl.substring(0, trimmedUrl.length() - 1);
        }

        // Base domain handling
        String base = trimmedUrl;

        // If user provided a full path including /v1, strip it to base
        if (base.contains("/v1")) {
            base = base.substring(0, base.indexOf("/v1"));
        } else if (base.endsWith("/chat/completions")) {
            // Handle cases where user pasted the full OpenAI endpoint
            base = base.substring(0, base.indexOf("/chat/completions"));
            if (base.endsWith("/v1")) {
                base = base.substring(0, base.length() - 3);
            }
        }

        // Ensure path starts with /
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        // Construct final URL: base + /v1 + path
        // SiliconFlow standard: https://api.siliconflow.cn/v1/video/submit
        return base + "/v1" + path;
    }
}
