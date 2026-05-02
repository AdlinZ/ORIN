package com.adlin.orin.modules.integrationsync.connector;

import com.adlin.orin.modules.integrationsync.adapter.DifyWorkflowAdapter;
import com.adlin.orin.modules.integrationsync.model.*;
import com.adlin.orin.modules.knowledge.service.sync.DifyFullApiClient;
import com.adlin.orin.modules.workflow.converter.DifyDslConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DifySyncConnectorTest {

    private final DifyFullApiClient apiClient = mock(DifyFullApiClient.class);
    private final DifySyncConnector connector = new DifySyncConnector(
            mock(RestTemplate.class),
            new ObjectMapper(),
            apiClient,
            new DifyWorkflowAdapter(new DifyDslConverter(), new ObjectMapper()));

    @Test
    void push_ShouldCreateDifyDocumentAndReturnCompletedItem() {
        IntegrationConnection connection = IntegrationConnection.builder()
                .integrationId(1L)
                .platformType(PlatformType.DIFY)
                .baseUrl("https://dify.test")
                .authConfig("{\"apiKey\":\"secret\"}")
                .build();
        ExternalResource document = ExternalResource.builder()
                .orinResourceType(SyncResourceType.DOCUMENT)
                .orinResourceId("doc-local")
                .externalResourceType("document")
                .name("Doc")
                .contentHash("hash")
                .canonicalSnapshot(Map.of(
                        "externalDatasetId", "ds-1",
                        "content", "hello",
                        "name", "Doc"))
                .build();

        when(apiClient.createDocument("https://dify.test", "secret", "ds-1", "Doc", "hello"))
                .thenReturn("dify-doc-1");
        when(apiClient.uploadDocumentContent("https://dify.test", "secret", "ds-1", "dify-doc-1", "hello"))
                .thenReturn(true);

        PushResult result = connector.push(SyncPushRequest.builder()
                .connection(connection)
                .resources(List.of(document))
                .build());

        assertTrue(result.isSuccess());
        assertEquals(SyncStatus.COMPLETED, result.getStatus());
        assertEquals("dify-doc-1", result.getItems().get(0).getExternalResourceId());
        assertEquals(SyncStatus.COMPLETED, result.getItems().get(0).getStatus());
    }
}
