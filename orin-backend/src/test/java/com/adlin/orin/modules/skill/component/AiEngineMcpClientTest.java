package com.adlin.orin.modules.skill.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AiEngineMcpClientTest {

    private AiEngineMcpClient client;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        client = new AiEngineMcpClient(new ObjectMapper());
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        ReflectionTestUtils.setField(client, "aiEngineUrl", "http://ai-engine:8000");
    }

    @Test
    void callTool_success_returnsSerializedResult() {
        mockServer.expect(requestTo(containsString("/api/mcp/services/7/tools/get_current_time/call")))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.arguments.timezone").value("UTC"))
                .andRespond(withSuccess(
                        "{\"serviceId\":7,\"toolName\":\"get_current_time\",\"result\":{\"now\":\"2026-05-14T00:00:00Z\"}}",
                        MediaType.APPLICATION_JSON));

        String result = client.callTool(7L, "get_current_time", Map.of("timezone", "UTC"));

        assertTrue(result.contains("2026-05-14T00:00:00Z"), "should pass through MCP result payload");
        mockServer.verify();
    }

    @Test
    void callTool_aiEngine502_throwsClassifiedException() {
        mockServer.expect(requestTo(containsString("/tools/fetch/call")))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("mcp server crashed"));

        AiEngineMcpClient.McpToolCallException ex = assertThrows(
                AiEngineMcpClient.McpToolCallException.class,
                () -> client.callTool(3L, "fetch", Map.of("url", "https://example.com")));

        assertTrue(ex.getMessage().contains("502"));
        assertTrue(ex.getMessage().contains("mcp server crashed"));
    }

    @Test
    void callTool_aiEngine500_throwsClassifiedException() {
        mockServer.expect(requestTo(containsString("/tools/fetch/call")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("boom"));

        AiEngineMcpClient.McpToolCallException ex = assertThrows(
                AiEngineMcpClient.McpToolCallException.class,
                () -> client.callTool(3L, "fetch", Map.of()));

        assertTrue(ex.getMessage().contains("500"));
    }

    @Test
    void callTool_unreachable_throwsClassifiedException() {
        mockServer.expect(requestTo(containsString("/tools/fetch/call")))
                .andRespond(withException(new IOException("connection refused")));

        AiEngineMcpClient.McpToolCallException ex = assertThrows(
                AiEngineMcpClient.McpToolCallException.class,
                () -> client.callTool(3L, "fetch", Map.of()));

        assertTrue(ex.getMessage().contains("超时") || ex.getMessage().contains("不可达"));
    }
}
