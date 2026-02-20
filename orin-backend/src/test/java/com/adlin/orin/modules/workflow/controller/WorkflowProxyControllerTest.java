package com.adlin.orin.modules.workflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WorkflowProxyControllerTest {

    public static MockWebServer mockBackEnd;

    @Autowired
    private WorkflowProxyController controller;

    // We can't use WebTestClient against the controller directly easily because it
    // returns Mono<ResponseEntity>
    // and relies on an injected WebClient.
    // Instead, let's test the controller method logic by calling it directly
    // or use a real WebClient to call the controller (via local server port).
    // Actually, since we want to verifying the Proxying, testing the Controller
    // method is enough.

    @BeforeAll
    static void setUp() throws IOException {
        mockBackEnd = new MockWebServer();
        mockBackEnd.start();
    }

    @AfterAll
    static void tearDown() throws IOException {
        mockBackEnd.shutdown();
    }

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("orin.ai-engine.url", () -> "http://localhost:" + mockBackEnd.getPort());
    }

    @Test
    void testRunWorkflowProxy_Success() throws Exception {
        // Prepare Mock Response from Python Engine
        String mockPythonResponse = "{\"success\": true, \"outputs\": {\"1\": \"result\"}}";
        mockBackEnd.enqueue(new MockResponse()
                .setBody(mockPythonResponse)
                .addHeader("Content-Type", "application/json"));

        // Prepare Input DSL
        ObjectMapper mapper = new ObjectMapper();
        var dsl = mapper.readTree("{\"nodes\": [], \"edges\": []}");

        // Execute Controller Method
        var responseEntity = controller.runWorkflowProxy(dsl,
                new org.springframework.mock.web.MockHttpServletRequest());

        // Verify
        assertThat(responseEntity).isNotNull();
        assertThat(responseEntity.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(responseEntity.getBody().get("success").asBoolean()).isTrue();

        // Construct request check
        var recordedRequest = mockBackEnd.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
        assertThat(recordedRequest.getPath()).isEqualTo("/api/v1/run");
    }
}
