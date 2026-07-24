package com.adlin.orin.modules.endpoint;

import com.adlin.orin.BaseIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * F05 Endpoint contract tests.
 *
 * <p>Verifies request/response shapes and error codes for the Endpoint API.
 * F05 is Backend Only — these tests validate the contract, not real execution.
 */
class EndpointContractTest extends BaseIntegrationTest {

    @Test
    void listEndpoints_ReturnsPaginatedResponse() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/endpoints")
                        .header("Authorization", "Bearer " + jwtCreator())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(root.has("content"));
    }

    @Test
    void getEndpoint_NotFound_Returns404() throws Exception {
        mockMvc.perform(get("/api/v1/endpoints/nonexistent-endpoint")
                        .header("Authorization", "Bearer " + jwtCreator()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("150001")); // ENDPOINT_NOT_FOUND
    }

    @Test
    void publishEndpoint_ValidationFailure_Returns400() throws Exception {
        // Missing required fields (name, agentId, agentVersionId)
        mockMvc.perform(post("/api/v1/endpoints")
                        .header("Authorization", "Bearer " + jwtCreator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publishEndpoint_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(post("/api/v1/endpoints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"test\",\"agentId\":\"a\",\"agentVersionId\":\"v\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deactivateEndpoint_NotFound_Returns404() throws Exception {
        mockMvc.perform(post("/api/v1/endpoints/nonexistent/deactivate")
                        .header("Authorization", "Bearer " + jwtCreator()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("150001"));
    }

    @Test
    void activateEndpoint_NotFound_Returns404() throws Exception {
        mockMvc.perform(post("/api/v1/endpoints/nonexistent/activate")
                        .header("Authorization", "Bearer " + jwtCreator()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("150001"));
    }
}
