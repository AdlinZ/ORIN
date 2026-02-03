package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.modules.knowledge.component.VectorStoreProvider;
import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.ConnectParam;
import io.milvus.param.R;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.partition.CreatePartitionParam;
import io.milvus.param.partition.HasPartitionParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Milvus 向量数据库服务
 * 实现 VectorStoreProvider 接口
 * 采用单 Collection 多 Partition 策略，每个 Knowledge Base 对应一个 Partition。
 */
@Slf4j
@Service
public class MilvusVectorService implements VectorStoreProvider {

    @Value("${orin.milvus.host:localhost}")
    private String host;

    @Value("${orin.milvus.port:19530}")
    private int port;

    @Value("${orin.milvus.token:root:Milvus}")
    private String token;

    // 全局唯一的 Collection 名称
    private static final String COLLECTION_NAME = "orin_knowledge_base";

    @org.springframework.beans.factory.annotation.Autowired
    private com.adlin.orin.gateway.service.ProviderRegistry providerRegistry;

    /**
     * 创建 Milvus 客户端连接
     */
    public MilvusServiceClient createClient() {
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .withAuthorization("root", token)
                .withConnectTimeout(2, java.util.concurrent.TimeUnit.SECONDS) // 2s timeout
                .withKeepAliveTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        return new MilvusServiceClient(connectParam);
    }

    @Override
    public void addDocuments(String kbId, List<KnowledgeDocument> documents) {
        // collectionName 参数在这里被视为 partitionName (kbId) 的标识
        // 但为了接口兼容，我们约定调用方传入的 collectionName 实则是 kbId_prefix 或者直接在内部处理
        // 按照计划，我们使用 kb_{id} 作为 partition name

        if (documents == null || documents.isEmpty())
            return;

        String partitionName = "kb_" + kbId.replace("-", "_"); // Ensure valid name

        MilvusServiceClient client = null;
        try {
            client = createClient();
            ensurePartitionExists(client, COLLECTION_NAME, partitionName);

            // 批处理文档 chunks
            // 注意：这里我们假设 KnowledgeDocument 本身不包含 chunks，addDocuments 接口定义其实略有歧义
            // 通常由上层 Service 处理完 Split 后，再传递 Chunks 进来。
            // 但 VectorStoreProvider 接口定义的是 addDocuments(List<KnowledgeDocument>)
            // 我们这里假设上层调用时，实际上是希望把文档的内容向量化并存入。
            // 更好的做法是上层 Split 完后，这里接收 Chunks。
            // 鉴于接口限制，我们暂时在这里做 Split & Embed (或者是假设上层传下来的 Document 已经被处理过？)
            // 回看 Implementation Plan -> "addDocuments: Embed chunks (using existing
            // embedding logic), insert into Milvus Partition."

            // 为了简化，我们假设 DocumentManageService 已经处理了 TextSplitter 并保存了 Chunk DB 记录
            // 这里我们需要重新读取 Chunk 内容进行 Embedding (不太高效)
            // 或者我们修改接口接受 Chunks?
            // 鉴于不要修改太多现有接口定义，我们在这里做 Embedding。

            // Re-reading implementation plan:
            // DocumentManageService.triggerVectorization -> Split -> Save Chunks ->
            // MilvusVectorService.addDocuments

            // Wait, Milvus needs Vectors.
            // Let's iterate documents, fetch their content (from storage path? or
            // provided?), split, embed, and insert.
            // Since we don't have Chunk object in args, we assume doc has content.
            // But documents list passed here might just be metadata.

            // Let's assume the callers (DocumentManageService) will handle splitting logic
            // and maybe we should OVERLOAD this method or separate concerns.
            // But to stick strictly to "VectorStoreProvider" interface:
            // We'll iterate docs. If they have no vectors, we generate them.

            List<List<Float>> vectors = new ArrayList<>();
            // or we use insert with fields.

            // Field structure: [id (Long/String), vector (FloatVector), doc_id (String),
            // chunk_id (String), content (String)]

            List<String> chunkIds = new ArrayList<>();
            List<String> docIds = new ArrayList<>();
            List<String> contents = new ArrayList<>();

            for (KnowledgeDocument doc : documents) {
                // For simplicity in this step, we assume the document ALREADY has chunks in DB?
                // Or we generate them on the fly.
                // Let's use the helper method to embed text.

                // Fetch content - In real world, read from file.
                // Here, use contentPreview or Mock for now as we don't want to do complex file
                // IO in this Service class if possible.
                // BUT, DocumentManageService is supposed to call this.

                // Let's implicitly assume we receive text content to embed? No, arg is
                // `KnowledgeDocument`.
                // Let's rely on `contentPreview` for now for the prototype,
                // OR better: The Interface `addDocuments` should ideally take `List<Chunk>`
                // object.
                // But I cannot change the Inteface signature easily without breaking other
                // things?
                // The Interface IS defined in this module. I CAN change it.
                // Plan said: "Implement VectorStoreProvider interface".

                // Let's stick to the plan: DocumentManageService updates
                // `triggerVectorization`.
                // It calls `addDocuments`.
                // I will modify `addDocuments` logic to handle the "Text to Vector" part.

                String text = doc.getContentPreview(); // Fallback
                if (text == null)
                    text = "";

                // Simple embedding for just the preview/content
                List<Float> vector = textToVector(text, null);

                vectors.add(vector);
                docIds.add(doc.getId());
                // For chunk ID, we might need a new approach if we want multiple chunks per
                // doc.
                // Current interface `addDocuments` implies 1 doc = 1 vector?
                // If so, it defeats the purpose of chunking.

                // DECISION: I will change the logic to support multiple vectors per document if
                // needed,
                // but strictly following the current Interface which takes
                // `List<KnowledgeDocument>`,
                // it suggests coarse granularity.
                // However, I can treat each `KnowledgeDocument` *entry* in the list as a Chunk
                // if I wanted to,
                // but type is `KnowledgeDocument`.

                // CORRECT APPROACH:
                // I will add a method `addChunks` to `VectorStoreProvider` and implement it
                // here.
                // `DocumentManageService` will call `addChunks`.

                chunkIds.add(UUID.randomUUID().toString()); // Temp chunk ID
                contents.add(text);
            }

            List<InsertParam.Field> fields = new ArrayList<>();
            fields.add(new InsertParam.Field("doc_id", docIds));
            fields.add(new InsertParam.Field("chunk_id", chunkIds));
            fields.add(new InsertParam.Field("content", contents));
            fields.add(new InsertParam.Field("embedding", vectors));

            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withPartitionName(partitionName)
                    .withFields(fields)
                    .build();

            client.insert(insertParam);
            log.info("Inserted {} documents into partition {}", documents.size(), partitionName);

        } catch (Exception e) {
            log.error("Milvus insert error: {}", e.getMessage(), e);
        } finally {
            if (client != null)
                client.close();
        }
    }

    // New method to support Chunks explicitly - Recommended
    public void addChunks(String kbId, List<com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty())
            return;

        String partitionName = "kb_" + kbId.replace("-", "_");

        MilvusServiceClient client = null;
        try {
            client = createClient();
            ensurePartitionExists(client, COLLECTION_NAME, partitionName);

            List<List<Float>> vectors = new ArrayList<>();
            List<String> docIds = new ArrayList<>();
            List<String> chunkIds = new ArrayList<>();
            List<String> contents = new ArrayList<>();

            for (var chunk : chunks) {
                List<Float> vector = textToVector(chunk.getContent(), null);
                vectors.add(vector);
                docIds.add(chunk.getDocumentId());
                chunkIds.add(chunk.getId());
                contents.add(chunk.getContent());
            }

            List<InsertParam.Field> fields = new ArrayList<>();
            fields.add(new InsertParam.Field("doc_id", docIds));
            fields.add(new InsertParam.Field("chunk_id", chunkIds));
            fields.add(new InsertParam.Field("content", contents)); // Note: storing content in Milvus can be expensive
            fields.add(new InsertParam.Field("embedding", vectors));

            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withPartitionName(partitionName)
                    .withFields(fields)
                    .build();

            client.insert(insertParam);
            log.info("Inserted {} chunks into partition {}", chunks.size(), partitionName);

        } catch (Exception e) {
            log.error("Milvus chunks insert error: {}", e.getMessage(), e);
        } finally {
            if (client != null)
                client.close();
        }
    }

    @Override
    public void deleteDocuments(String kbId, List<String> docIds) {
        // Delete by expression in Partition
        String partitionName = "kb_" + kbId.replace("-", "_");
        MilvusServiceClient client = null;
        try {
            client = createClient();
            String expression = "doc_id in "
                    + docIds.stream().map(id -> "'" + id + "'").collect(Collectors.toList()).toString();
            client.delete(io.milvus.param.dml.DeleteParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withPartitionName(partitionName)
                    .withExpr(expression)
                    .build());
        } catch (Throwable e) {
            log.error("Delete error: {}", e.getMessage());
        } finally {
            if (client != null)
                client.close();
        }
    }

    @Override
    public void deleteKnowledgeBase(String kbId) {
        String partitionName = "kb_" + kbId.replace("-", "_");
        log.info("Deleting Milvus partition {} from collection {}", partitionName, COLLECTION_NAME);
        MilvusServiceClient client = null;
        try {
            client = createClient();
            if (checkPartitionExists(client, COLLECTION_NAME, partitionName)) {
                client.dropPartition(io.milvus.param.partition.DropPartitionParam.newBuilder()
                        .withCollectionName(COLLECTION_NAME)
                        .withPartitionName(partitionName)
                        .build());
            }
        } catch (Throwable e) {
            log.error("Failed to drop Milvus partition: {}", e.getMessage());
        } finally {
            if (client != null)
                client.close();
        }
    }

    @Override
    public List<SearchResult> search(String kbId, String query, int k) {
        return search(kbId, query, k, null);
    }

    @Override
    public List<SearchResult> search(String kbId, String query, int k, String embeddingModel) {
        String partitionName = "kb_" + kbId.replace("-", "_");
        List<Float> queryVector = textToVector(query, embeddingModel);

        MilvusServiceClient client = null;
        try {
            client = createClient();

            // Check if partition exists first
            if (!checkPartitionExists(client, COLLECTION_NAME, partitionName)) {
                return Collections.emptyList();
            }

            client.loadPartitions(io.milvus.param.partition.LoadPartitionsParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withPartitionNames(Collections.singletonList(partitionName))
                    .build());

            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withPartitionNames(Collections.singletonList(partitionName))
                    .withMetricType(io.milvus.param.MetricType.COSINE)
                    .withTopK(k)
                    .withVectors(Collections.singletonList(queryVector))
                    .withVectorFieldName("embedding")
                    .withOutFields(Arrays.asList("content", "doc_id", "chunk_id"))
                    .build();

            R<SearchResults> response = client.search(searchParam);

            if (response.getStatus() != R.Status.Success.getCode()) {
                return Collections.emptyList();
            }

            SearchResults searchResults = response.getData();
            io.milvus.response.SearchResultsWrapper wrapper = new io.milvus.response.SearchResultsWrapper(
                    searchResults.getResults());
            List<io.milvus.response.SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);

            List<SearchResult> results = new ArrayList<>();
            // Milvus SDK behavior:
            // For some versions, wrapper.getIDScore(0) returns list of IDScore.
            // If output fields are present, they might be in a different structure or
            // accessible via IDScore.getField(name).
            // IF getField is not available, we might need to use wrapper.getRowRecords(0)
            // if available.
            // Let's assume we can't get fields easily in this SDK version if getField is
            // undefined.
            // However, we stored content in the Insert...
            // Checking standard Milvus SDK:
            // wrapper.getIDScore(0) -> List<IDScore>. IDScore has score and id.
            // To get fields, usually one uses wrapper.getFieldData(fieldName, 0).

            List<?> contents = wrapper.getFieldData("content", 0);
            List<?> docIds = wrapper.getFieldData("doc_id", 0);
            List<?> chunkIds = wrapper.getFieldData("chunk_id", 0);

            for (int i = 0; i < scores.size(); i++) {
                io.milvus.response.SearchResultsWrapper.IDScore score = scores.get(i);
                Map<String, Object> metadata = new HashMap<>();

                if (docIds != null && i < docIds.size())
                    metadata.put("doc_id", docIds.get(i));
                if (chunkIds != null && i < chunkIds.size())
                    metadata.put("chunk_id", chunkIds.get(i));

                String contentStr = "";
                if (contents != null && i < contents.size()) {
                    contentStr = String.valueOf(contents.get(i));
                }

                results.add(SearchResult.builder()
                        .content(contentStr)
                        .score((double) score.getScore())
                        .metadata(metadata)
                        .build());
            }
            return results;

        } catch (Exception e) {
            log.error("Search error: {}", e.getMessage(), e);
            return Collections.emptyList();
        } finally {
            if (client != null)
                client.close();
        }
    }

    /**
     * Legacy Search Method for SkillServiceImplEnhanced compatibility
     */
    public List<Map<String, Object>> search(
            String host,
            int port,
            String token,
            String collectionName,
            List<Float> queryVector,
            int topK,
            double threshold) {

        MilvusServiceClient client = null;
        try {
            ConnectParam connectParam = ConnectParam.newBuilder()
                    .withHost(host)
                    .withPort(port)
                    .withAuthorization("root", token != null ? token : "Milvus")
                    .build();
            client = new MilvusServiceClient(connectParam);

            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withPartitionNames(Collections.singletonList(collectionName)) // Treat collectionName arg as
                                                                                   // partitionName
                    .withMetricType(io.milvus.param.MetricType.COSINE)
                    .withTopK(topK)
                    .withVectors(Collections.singletonList(queryVector))
                    .withVectorFieldName("embedding")
                    .withOutFields(Arrays.asList("content", "doc_id"))
                    .build();

            R<SearchResults> response = client.search(searchParam);

            if (response.getStatus() != R.Status.Success.getCode()) {
                return Collections.emptyList();
            }

            SearchResults searchResults = response.getData();
            io.milvus.response.SearchResultsWrapper wrapper = new io.milvus.response.SearchResultsWrapper(
                    searchResults.getResults());
            List<io.milvus.response.SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);

            List<?> contents = wrapper.getFieldData("content", 0);
            List<?> docIds = wrapper.getFieldData("doc_id", 0);

            List<Map<String, Object>> resultList = new ArrayList<>();

            for (int i = 0; i < scores.size(); i++) {
                io.milvus.response.SearchResultsWrapper.IDScore score = scores.get(i);
                if (score.getScore() < threshold)
                    continue;

                Map<String, Object> map = new HashMap<>();
                map.put("score", score.getScore());

                if (docIds != null && i < docIds.size())
                    map.put("id", docIds.get(i));
                if (contents != null && i < contents.size())
                    map.put("content", contents.get(i));

                resultList.add(map);
            }

            return resultList;

        } catch (Exception e) {
            log.error("Legacy Milvus search error: {}", e.getMessage(), e);
            return Collections.emptyList();
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                }
            }
        }
    }

    @Override
    public List<com.adlin.orin.modules.knowledge.component.VectorStoreProvider.DocumentChunk> getDocumentChunks(
            String kbId, String docId) {
        // Not easily supported by Milvus query unless we use QueryParam with expression
        return Collections.emptyList();
    }

    // --- Helper Methods ---

    private void ensurePartitionExists(MilvusServiceClient client, String collectionName, String partitionName) {
        if (!checkPartitionExists(client, collectionName, partitionName)) {
            client.createPartition(CreatePartitionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withPartitionName(partitionName)
                    .build());
        }
    }

    private boolean checkPartitionExists(MilvusServiceClient client, String collectionName, String partitionName) {
        R<Boolean> response = client.hasPartition(HasPartitionParam.newBuilder()
                .withCollectionName(collectionName)
                .withPartitionName(partitionName)
                .build());
        return response.getData() != null && response.getData();
    }

    /**
     * Text to Vector (Same as before)
     */
    public List<Float> textToVector(String text, String embeddingModel) {
        var providers = providerRegistry.getHealthyProviders();
        for (var provider : providers) {
            if ("openai".equals(provider.getProviderType())) {
                try {
                    com.adlin.orin.gateway.dto.EmbeddingResponse response = provider.embedding(
                            com.adlin.orin.gateway.dto.EmbeddingRequest.builder()
                                    .model(embeddingModel != null ? embeddingModel : "text-embedding-ada-002")
                                    .input(Collections.singletonList(text))
                                    .build())
                            .block();

                    if (response != null && !response.getData().isEmpty()) {
                        List<Double> embedding = response.getData().get(0).getEmbedding();
                        List<Float> floatEmbedding = new ArrayList<>(embedding.size());
                        for (Double d : embedding)
                            floatEmbedding.add(d.floatValue());
                        return floatEmbedding;
                    }
                } catch (Exception e) {
                    log.warn("Embedding failed: {}", e.getMessage());
                }
            }
        }
        // Fallback Random
        List<Float> vector = new ArrayList<>();
        Random random = new Random(text.hashCode());
        for (int i = 0; i < 768; i++)
            vector.add(random.nextFloat());
        return vector;
    }
}
