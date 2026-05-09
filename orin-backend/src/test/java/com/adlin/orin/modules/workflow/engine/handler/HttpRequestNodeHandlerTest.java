package com.adlin.orin.modules.workflow.engine.handler;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpRequestNodeHandlerTest {

    @Test
    void execute_GetWithoutConfiguredBody_ShouldNotSendSyntheticEmptyBody() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        HttpRequestNodeHandler handler = new HttpRequestNodeHandler(restTemplate);
        String url = "https://example.com/api?q=hello";

        when(restTemplate.exchange(
                eq(url),
                eq(HttpMethod.GET),
                org.mockito.ArgumentMatchers.<HttpEntity<?>>any(),
                eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"ok\":true}", HttpStatus.OK));

        NodeExecutionResult result = handler.execute(Map.of(
                "url", url,
                "method", "GET",
                "headers", Map.of("Accept", "application/json")), Map.of());

        ArgumentCaptor<HttpEntity<?>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq(url), eq(HttpMethod.GET), entityCaptor.capture(), eq(String.class));

        assertThat(entityCaptor.getValue().getBody()).isNull();
        assertThat(result.getOutputs()).containsEntry("statusCode", 200);
        assertThat(result.getOutputs()).containsEntry("body", "{\"ok\":true}");
    }
}
