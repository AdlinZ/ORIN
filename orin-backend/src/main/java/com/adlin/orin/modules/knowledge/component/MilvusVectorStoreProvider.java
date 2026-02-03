package com.adlin.orin.modules.knowledge.component;

import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.SearchResults;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.param.IndexType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class MilvusVectorStoreProvider implements VectorStoreProvider {

    private final MilvusServiceClient milvusClient;
    private static final int DIMENSION = 1536; // OpenAI Ada-002 dimension

    @Override
    public void addDocuments(String collectionName, List<KnowledgeDocument> documents) {
        // Not used directly in current flow, we insert chunks
    }

    @Override
    public void addChunks(String kbId, List<com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk> chunks) {
        String collectionName = "kb_" + kbId;
        for (var chunk : chunks) {
            insertChunk(chunk.getDocumentId(), chunk.getContent(), collectionName);
        }
    }

    // Custom method to insert chunks directly
    public void insertChunk(String docId, String content, String collectionName) {
        ensureCollectionExists(collectionName);

        List<List<Float>> vectors = new ArrayList<>();
        vectors.add(mockEmbedding(content)); // MOCK EMBEDDING

        List<io.milvus.param.dml.InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field("doc_id", Collections.singletonList(docId)));
        fields.add(new InsertParam.Field("content", Collections.singletonList(content)));
        fields.add(new InsertParam.Field("vector", vectors));

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(collectionName)
                .withFields(fields)
                .build();

        R<io.milvus.grpc.MutationResult> result = milvusClient.insert(insertParam);
        if (result.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Failed to insert into Milvus: " + result.getMessage());
        }
    }

    @Override
    public List<SearchResult> search(String collectionName, String query, int k) {
        if (!hasCollection(collectionName)) {
            return Collections.emptyList();
        }

        List<Float> queryVector = mockEmbedding(query);

        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(collectionName)
                .withMetricType(MetricType.L2)
                .withTopK(k)
                .withVectors(Collections.singletonList(queryVector))
                .withVectorFieldName("vector")
                .addOutField("content")
                .addOutField("doc_id")
                .build();

        R<SearchResults> response = milvusClient.search(searchParam);
        if (response.getStatus() != R.Status.Success.getCode()) {
            log.error("Milvus search failed: {}", response.getMessage());
            return Collections.emptyList();
        }

        SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
        List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);

        return scores.stream().map(score -> {
            String content = (String) score.get("content");
            return SearchResult.builder()
                    .content(content)
                    .score((double) score.getScore())
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public List<DocumentChunk> getDocumentChunks(String collectionName, String docId) {
        return Collections.emptyList();
    }

    @Override
    public void deleteDocuments(String collectionName, List<String> docIds) {
        log.info("Deleting documents from Milvus collection {}: {}", collectionName, docIds);
        String expr = "doc_id in " + docIds.stream().map(id -> "\"" + id + "\"").collect(Collectors.toList());
        try {
            milvusClient.delete(io.milvus.param.dml.DeleteParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withExpr(expr)
                    .build());
        } catch (Throwable e) {
            log.error("Milvus delete documents error: {}", e.getMessage());
        }
    }

    @Override
    public void deleteKnowledgeBase(String kbId) {
        String collectionName = "kb_" + kbId;
        log.info("Deleting Milvus collection: {}", collectionName);
        try {
            if (hasCollection(collectionName)) {
                milvusClient.dropCollection(io.milvus.param.collection.DropCollectionParam.newBuilder()
                        .withCollectionName(collectionName)
                        .build());
            }
        } catch (Throwable e) {
            log.error("Milvus drop collection error: {}", e.getMessage());
        }
    }

    private synchronized void ensureCollectionExists(String collectionName) {
        if (hasCollection(collectionName))
            return;

        FieldType docIdField = FieldType.newBuilder()
                .withName("doc_id")
                .withDataType(DataType.VarChar)
                .withMaxLength(64)
                .withPrimaryKey(false)
                .build();

        FieldType dbIdField = FieldType.newBuilder()
                .withName("db_id")
                .withDataType(DataType.Int64)
                .withPrimaryKey(true)
                .withAutoID(true)
                .build();

        FieldType contentField = FieldType.newBuilder()
                .withName("content")
                .withDataType(DataType.VarChar)
                .withMaxLength(2048)
                .build();

        FieldType vectorField = FieldType.newBuilder()
                .withName("vector")
                .withDataType(DataType.FloatVector)
                .withDimension(DIMENSION)
                .build();

        CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .addFieldType(dbIdField)
                .addFieldType(docIdField)
                .addFieldType(contentField)
                .addFieldType(vectorField)
                .build();

        milvusClient.createCollection(createParam);

        CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName("vector")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.L2)
                .withExtraParam("{\"nlist\":1024}")
                .build();

        milvusClient.createIndex(indexParam);

        milvusClient.loadCollection(io.milvus.param.collection.LoadCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build());
    }

    private boolean hasCollection(String collectionName) {
        try {
            R<Boolean> response = milvusClient.hasCollection(HasCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build());
            return response.getData() != null && response.getData();
        } catch (Throwable e) {
            log.error("Milvus hasCollection error: {}", e.getMessage());
            return false;
        }
    }

    private List<Float> mockEmbedding(String text) {
        List<Float> vector = new ArrayList<>(DIMENSION);
        for (int i = 0; i < DIMENSION; i++) {
            vector.add((float) Math.random());
        }
        return vector;
    }
}
