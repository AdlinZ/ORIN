package com.adlin.orin.modules.integrationsync.service;

import com.adlin.orin.modules.agent.service.AgentOwnershipResolver;
import com.adlin.orin.modules.integrationsync.adapter.DifyWorkflowAdapter;
import com.adlin.orin.modules.integrationsync.entity.ExternalResourceMapping;
import com.adlin.orin.modules.integrationsync.model.ExternalResource;
import com.adlin.orin.modules.integrationsync.model.IntegrationConnection;
import com.adlin.orin.modules.integrationsync.model.PlatformType;
import com.adlin.orin.modules.integrationsync.model.SyncResourceType;
import com.adlin.orin.modules.integrationsync.repository.ExternalResourceMappingRepository;
import com.adlin.orin.modules.knowledge.entity.KnowledgeBase;
import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import com.adlin.orin.modules.knowledge.repository.KnowledgeBaseRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository;
import com.adlin.orin.modules.workflow.converter.DifyDslConverter;
import com.adlin.orin.modules.workflow.repository.WorkflowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DifyResourceImportServiceTest {

    private final WorkflowRepository workflowRepository = mock(WorkflowRepository.class);
    private final KnowledgeBaseRepository knowledgeBaseRepository = mock(KnowledgeBaseRepository.class);
    private final KnowledgeDocumentRepository documentRepository = mock(KnowledgeDocumentRepository.class);
    private final ExternalResourceMappingRepository mappingRepository = mock(ExternalResourceMappingRepository.class);
    private final AgentOwnershipResolver ownershipResolver = mock(AgentOwnershipResolver.class);
    private final DifyResourceImportService service = new DifyResourceImportService(
            workflowRepository,
            knowledgeBaseRepository,
            documentRepository,
            mappingRepository,
            new DifyWorkflowAdapter(new DifyDslConverter(), new ObjectMapper()),
            new ObjectMapper(),
            ownershipResolver);

    @Test
    void importResource_ShouldCreateKnowledgeBaseFromDifyDataset() {
        IntegrationConnection connection = IntegrationConnection.builder()
                .integrationId(9L)
                .platformType(PlatformType.DIFY)
                .build();
        ExternalResource resource = ExternalResource.builder()
                .orinResourceType(SyncResourceType.KNOWLEDGE_BASE)
                .externalResourceType("dataset")
                .externalResourceId("ds-1")
                .name("Dataset")
                .canonicalSnapshot(Map.of("name", "Dataset", "description", "Desc", "documentCount", 3))
                .rawSnapshot(Map.of("id", "ds-1"))
                .build();

        when(knowledgeBaseRepository.findById("dify-9-ds-1")).thenReturn(Optional.empty());
        when(knowledgeBaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String orinId = service.importResource(connection, resource, null);

        assertEquals("dify-9-ds-1", orinId);
        verify(knowledgeBaseRepository).save(argThat(kb ->
                "dify-9-ds-1".equals(kb.getId())
                        && "Dataset".equals(kb.getName())
                        && Integer.valueOf(3).equals(kb.getDocCount())));
    }

    @Test
    void importResource_ShouldCreateDocumentAndResolveDatasetMapping() {
        IntegrationConnection connection = IntegrationConnection.builder()
                .integrationId(9L)
                .platformType(PlatformType.DIFY)
                .build();
        ExternalResource resource = ExternalResource.builder()
                .orinResourceType(SyncResourceType.DOCUMENT)
                .externalResourceType("document")
                .externalResourceId("doc-1")
                .name("Doc")
                .contentHash("hash")
                .canonicalSnapshot(Map.of(
                        "name", "Doc",
                        "externalDatasetId", "ds-1",
                        "content", "hello world",
                        "wordCount", 2))
                .rawSnapshot(Map.of("id", "doc-1"))
                .build();
        ExternalResourceMapping datasetMapping = ExternalResourceMapping.builder()
                .orinResourceId("kb-1")
                .build();

        when(mappingRepository.findByIntegrationIdAndPlatformTypeAndExternalResourceTypeAndExternalResourceId(9L, "DIFY", "dataset", "ds-1"))
                .thenReturn(Optional.of(datasetMapping));
        when(documentRepository.findById("dify-doc-9-doc-1")).thenReturn(Optional.empty());
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String orinId = service.importResource(connection, resource, null);

        assertEquals("dify-doc-9-doc-1", orinId);
        verify(documentRepository).save(argThat(doc ->
                "kb-1".equals(doc.getKnowledgeBaseId())
                        && "Doc".equals(doc.getFileName())
                        && "hello world".equals(doc.getContentPreview())));
    }
}
