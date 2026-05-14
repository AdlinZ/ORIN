package com.adlin.orin.modules.skill.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 调用 AI Engine 的通用 MCP 执行接口，把任意 MCP Server 的 tool 调用统一下沉到
 * AI Engine 的 {@code MCPClientManager}，后端不再为单个 MCP 类型写特例适配。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiEngineMcpClient {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = buildRestTemplate();

    @Value("${orin.ai-engine.url:http://127.0.0.1:8000}")
    private String aiEngineUrl;

    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(35_000);
        return new RestTemplate(factory);
    }

    /**
     * 通过 AI Engine 调用指定 MCP 服务的某个 tool。
     *
     * @return AI Engine 返回的 {@code result} 负载序列化后的 JSON 字符串
     * @throws McpToolCallException 调用失败（连接超时、AI Engine 报错等）
     */
    public String callTool(Long serviceId, String toolName, Map<String, Object> arguments) {
        String url = aiEngineUrl.replaceAll("/+$", "")
                + "/api/mcp/services/" + serviceId + "/tools/"
                + UriUtils.encodePathSegment(toolName, StandardCharsets.UTF_8) + "/call";

        Map<String, Object> body = new HashMap<>();
        body.put("arguments", arguments != null ? arguments : Map.of());

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);
            Object result = response != null ? response.get("result") : null;
            if (result == null) {
                return "（MCP 工具调用成功，但无结果负载）";
            }
            return result instanceof String s ? s : objectMapper.writeValueAsString(result);
        } catch (ResourceAccessException e) {
            log.warn("MCP tool call timeout/unreachable: serviceId={}, tool={}, err={}",
                    serviceId, toolName, e.getMessage());
            throw new McpToolCallException(McpErrorCode.MCP_TIMEOUT,
                    "MCP 工具调用超时或 AI Engine 不可达: " + e.getMessage(), e);
        } catch (RestClientResponseException e) {
            log.warn("MCP tool call failed: serviceId={}, tool={}, status={}, body={}",
                    serviceId, toolName, e.getStatusCode(), e.getResponseBodyAsString());
            throw new McpToolCallException(McpErrorCode.MCP_HTTP_ERROR,
                    "MCP 工具调用失败 (HTTP " + e.getStatusCode().value() + "): "
                            + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.warn("MCP tool call error: serviceId={}, tool={}, err={}", serviceId, toolName, e.getMessage());
            throw new McpToolCallException(McpErrorCode.MCP_CALL_FAILED,
                    "MCP 工具调用异常: " + e.getMessage(), e);
        }
    }

    /** AI Engine MCP 调用失败时抛出，携带分类错误码。 */
    public static class McpToolCallException extends RuntimeException {
        private final McpErrorCode code;

        public McpToolCallException(McpErrorCode code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public McpErrorCode getCode() {
            return code;
        }
    }
}
