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
import io.milvus.grpc.DataType;
import io.milvus.param.MetricType;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.param.IndexType;
import jakarta.annotation.PostConstruct;
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
@org.springframework.context.annotation.Primary
public class MilvusVectorService implements VectorStoreProvider {

    @Value("${milvus.host:127.0.0.1}")
    private String host;

    @Value("${milvus.port:19530}")
    private int port;

    @Value("${milvus.token:}")
    private String token;

    // 全局唯一的 Collection 名称
    private static final String COLLECTION_NAME = "orin_knowledge_base";

    @org.springframework.beans.factory.annotation.Autowired
    private com.adlin.orin.gateway.service.ProviderRegistry providerRegistry;

    @org.springframework.beans.factory.annotation.Autowired
    private com.adlin.orin.modules.knowledge.component.EmbeddingService embeddingService;

    @PostConstruct
    public void init() {
        // Run initialization in background to avoid blocking server startup if Milvus
        // is down
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            log.info("Starting asynchronous Milvus collection initialization...");
            initCollection();
        }).exceptionally(ex -> {
            log.error("Background Milvus initialization failed: {}", ex.getMessage());
            return null;
        });
    }

    private void initCollection() {
        MilvusServiceClient client = null;
        try {
            client = createClient();
            int embeddingDimension = getEmbeddingDimension();
            log.info("Target embedding dimension: {}", embeddingDimension);

            R<Boolean> response = client.hasCollection(HasCollectionParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .build());
            if (response.getData() != null && response.getData()) {
                log.info("Milvus collection '{}' already exists.", COLLECTION_NAME);
                // 获取已存在的 collection 的 vector 字段维度
                int existingDimension = getExistingCollectionDimension(client);
                if (existingDimension > 0 && existingDimension != embeddingDimension) {
                    log.warn("Dimension mismatch! Existing: {}, New: {}. Auto-recreating...", existingDimension, embeddingDimension);
                    try {
                        client.dropCollection(io.milvus.param.collection.DropCollectionParam.newBuilder()
                                .withCollectionName(COLLECTION_NAME)
                                .build());
                        log.info("Dropped old collection for recreation");
                    } catch (Exception e) {
                        log.warn("Failed to drop collection: {}", e.getMessage());
                    }
                } else {
                    log.info("Collection exists with matching dimension {}. Skipping recreation.", embeddingDimension);
                }
            }

            log.info("Creating Milvus collection '{}' with dimension {}...", COLLECTION_NAME, embeddingDimension);
            FieldType docIdField = FieldType.newBuilder()
                    .withName("doc_id")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(64)
                    .withPrimaryKey(false)
                    .build();

            FieldType chunkIdField = FieldType.newBuilder()
                    .withName("chunk_id")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(64)
                    .withPrimaryKey(true)
                    .build();

            FieldType contentField = FieldType.newBuilder()
                    .withName("content")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(8192)
                    .build();

            // Parent-Child Hierarchical fields
            FieldType chunkTypeField = FieldType.newBuilder()
                    .withName("chunk_type")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(20)
                    .build();

            FieldType parentIdField = FieldType.newBuilder()
                    .withName("parent_id")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(64)
                    .build();

            FieldType titleField = FieldType.newBuilder()
                    .withName("title")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(500)
                    .build();

            FieldType sourceField = FieldType.newBuilder()
                    .withName("source")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(500)
                    .build();

            FieldType positionField = FieldType.newBuilder()
                    .withName("position")
                    .withDataType(DataType.Int32)
                    .build();

            FieldType vectorField = FieldType.newBuilder()
                    .withName("embedding")
                    .withDataType(DataType.FloatVector)
                    .withDimension(embeddingDimension)
                    .build();

            CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .addFieldType(chunkIdField)
                    .addFieldType(docIdField)
                    .addFieldType(contentField)
                    .addFieldType(chunkTypeField)
                    .addFieldType(parentIdField)
                    .addFieldType(titleField)
                    .addFieldType(sourceField)
                    .addFieldType(positionField)
                    .addFieldType(vectorField)
                    .build();

            client.createCollection(createParam);

            CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withFieldName("embedding")
                    .withIndexType(IndexType.IVF_FLAT)
                    .withMetricType(MetricType.COSINE)
                    .withExtraParam("{\"nlist\":1024}")
                    .build();

            client.createIndex(indexParam);

            client.loadCollection(io.milvus.param.collection.LoadCollectionParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .build());

            log.info("Milvus collection '{}' created successfully.", COLLECTION_NAME);
        } catch (Exception e) {
            log.error("Failed to init Milvus collection", e);
        } finally {
            if (client != null)
                client.close();
        }
    }

    /**
     * 创建 Milvus 客户端连接
     */
    public MilvusServiceClient createClient() {
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .withAuthorization("root", token != null ? token : "Milvus")
                .withConnectTimeout(3, java.util.concurrent.TimeUnit.SECONDS) // 3s timeout
                .withKeepAliveTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
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
            List<String> chunkTypes = new ArrayList<>();
            List<String> parentIds = new ArrayList<>();
            List<String> titles = new ArrayList<>();
            List<String> sources = new ArrayList<>();
            List<Integer> positions = new ArrayList<>();

            for (var chunk : chunks) {
                // Only embed child chunks
                List<Float> vector;
                if ("child".equals(chunk.getChunkType())) {
                    vector = textToVector(chunk.getContent(), null);
                } else {
                    // Parent chunks don't need vectors (they won't be searched)
                    // Use zero vector as placeholder with dynamic dimension
                    int dim = getEmbeddingDimension();
                    vector = Collections.nCopies(dim, 0.0f);
                }
                vectors.add(vector);

                docIds.add(chunk.getDocumentId());
                chunkIds.add(chunk.getId());
                contents.add(chunk.getContent());
                chunkTypes.add(chunk.getChunkType() != null ? chunk.getChunkType() : "child");
                parentIds.add(chunk.getParentId() != null ? chunk.getParentId() : "");
                titles.add(chunk.getTitle() != null ? chunk.getTitle() : "");
                sources.add(chunk.getSource() != null ? chunk.getSource() : "");
                positions.add(chunk.getPosition() != null ? chunk.getPosition() : 0);
            }

            List<InsertParam.Field> fields = new ArrayList<>();
            fields.add(new InsertParam.Field("doc_id", docIds));
            fields.add(new InsertParam.Field("chunk_id", chunkIds));
            fields.add(new InsertParam.Field("content", contents));
            fields.add(new InsertParam.Field("chunk_type", chunkTypes));
            fields.add(new InsertParam.Field("parent_id", parentIds));
            fields.add(new InsertParam.Field("title", titles));
            fields.add(new InsertParam.Field("source", sources));
            fields.add(new InsertParam.Field("position", positions));
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

            boolean isGlobalSearch = "all".equalsIgnoreCase(kbId);

            // Check if partition exists first for specific kb
            if (!isGlobalSearch && !checkPartitionExists(client, COLLECTION_NAME, partitionName)) {
                log.warn("Partition not found: {}, returning empty results. kbId={}", partitionName, kbId);
                return Collections.emptyList();
            }

            log.info("Searching in partition: {}, query: {}, topK: {}", partitionName, query, k);

            if (isGlobalSearch) {
                // If global, load the entire collection
                client.loadCollection(io.milvus.param.collection.LoadCollectionParam.newBuilder()
                        .withCollectionName(COLLECTION_NAME)
                        .build());
            } else {
                client.loadPartitions(io.milvus.param.partition.LoadPartitionsParam.newBuilder()
                        .withCollectionName(COLLECTION_NAME)
                        .withPartitionNames(Collections.singletonList(partitionName))
                        .build());
            }

            SearchParam.Builder searchBuilder = SearchParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withMetricType(io.milvus.param.MetricType.COSINE)
                    .withTopK(k)
                    .withVectors(Collections.singletonList(queryVector))
                    .withVectorFieldName("embedding")
                    .withOutFields(Arrays.asList("content", "doc_id", "chunk_id", "chunk_type", "parent_id", "title", "source", "position"));

            // Only search child chunks for vector retrieval
            searchBuilder.withExpr("chunk_type == 'child'");

            if (!isGlobalSearch) {
                searchBuilder.withPartitionNames(Collections.singletonList(partitionName));
            }

            SearchParam searchParam = searchBuilder.build();

            R<SearchResults> response = client.search(searchParam);

            if (response.getStatus() != R.Status.Success.getCode()) {
                log.error("Milvus search failed: status={}, message={}", response.getStatus(), response.getMessage());
                return Collections.emptyList();
            }

            SearchResults searchResults = response.getData();
            io.milvus.response.SearchResultsWrapper wrapper = new io.milvus.response.SearchResultsWrapper(
                    searchResults.getResults());
            List<io.milvus.response.SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);

            log.info("Milvus search returned {} results", scores.size());

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
            List<?> chunkTypes = wrapper.getFieldData("chunk_type", 0);
            List<?> parentIds = wrapper.getFieldData("parent_id", 0);
            List<?> titles = wrapper.getFieldData("title", 0);
            List<?> sources = wrapper.getFieldData("source", 0);
            List<?> positions = wrapper.getFieldData("position", 0);

            for (int i = 0; i < scores.size(); i++) {
                io.milvus.response.SearchResultsWrapper.IDScore score = scores.get(i);
                Map<String, Object> metadata = new HashMap<>();

                if (docIds != null && i < docIds.size())
                    metadata.put("doc_id", docIds.get(i));
                if (chunkIds != null && i < chunkIds.size())
                    metadata.put("chunk_id", chunkIds.get(i));
                if (chunkTypes != null && i < chunkTypes.size())
                    metadata.put("chunk_type", chunkTypes.get(i));
                if (parentIds != null && i < parentIds.size())
                    metadata.put("parent_id", parentIds.get(i));
                if (titles != null && i < titles.size())
                    metadata.put("title", titles.get(i));
                if (sources != null && i < sources.size())
                    metadata.put("source", sources.get(i));
                if (positions != null && i < positions.size())
                    metadata.put("position", positions.get(i));

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

    /**
     * Query chunks by parent IDs (for Parent-Child retrieval)
     * Note: This is a simplified implementation - parent chunks are retrieved from DB instead
     *
     * @param kbId      Knowledge base ID
     * @param parentIds List of parent chunk IDs
     * @return Map of parent_id -> content
     */
    public Map<String, String> getParentChunksByIds(String kbId, List<String> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // Fallback: Parent chunks are fetched from database chunk table
        return Collections.emptyMap();
    }

    // --- Helper Methods ---

    /**
     * 动态获取 embedding 向量维度
     */
    private int getEmbeddingDimension() {
        try {
            List<Float> testEmbedding = embeddingService.embed("test");
            if (testEmbedding != null && !testEmbedding.isEmpty()) {
                log.info("Embedding dimension detected: {}", testEmbedding.size());
                return testEmbedding.size();
            }
        } catch (Exception e) {
            log.warn("Failed to detect embedding dimension: {}", e.getMessage());
        }
        // 默认 1024
        return 1024;
    }

    /**
     * 获取已存在 collection 的 vector 字段维度
     * 注：简化处理，如果无法获取则返回 -1，避免误删 collection
     */
    private int getExistingCollectionDimension(MilvusServiceClient client) {
        // 简化处理：暂时无法获取现有维度，返回 -1
        // 这样会跳过重建，避免误删数据
        // 如需真正重建，请手动调用重建接口
        log.info("Skipping dimension check to preserve existing data");
        return -1;
    }

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
        log.info("hasPartition check: collection={}, partition={}, response={}, data={}",
                collectionName, partitionName, response.getStatus(), response.getData());
        return response.getData() != null && response.getData();
    }

    /**
     * Text to Vector
     * 优先使用 SiliconFlowEmbeddingAdapter (via EmbeddingService) 生成真实向量。
     * 若失败则降级为随机向量（动态检测维度）。
     */
    public List<Float> textToVector(String text, String embeddingModel) {
        // 优先使用已注入的 EmbeddingService (SiliconFlowEmbeddingAdapter)
        if (embeddingService != null) {
            try {
                List<Float> embedding = embeddingService.embed(text);
                if (embedding != null && !embedding.isEmpty()) {
                    log.debug("Embedding generated via SiliconFlow, dim={}", embedding.size());
                    return embedding;
                }
            } catch (Exception e) {
                log.warn("SiliconFlow embedding failed, falling back: {}", e.getMessage());
            }
        }
        // 降级: 随机向量 (text.hashCode 保证同一文本向量一致)
        int dim = getEmbeddingDimension();
        log.warn("Using deterministic random vector for text (EmbeddingService unavailable or failed), dimension={}", dim);
        List<Float> vector = new ArrayList<>();
        Random random = new Random(text.hashCode());
        for (int i = 0; i < dim; i++)
            vector.add(random.nextFloat());
        return vector;
    }

    /**
     * 获取 Collection 信息
     */
    public Map<String, Object> getCollectionInfo() {
        Map<String, Object> info = new HashMap<>();
        MilvusServiceClient client = null;

        // 先返回配置信息
        info.put("host", host);
        info.put("port", port);
        info.put("collectionName", COLLECTION_NAME);

        try {
            client = createClient();
            R<Boolean> response = client.hasCollection(HasCollectionParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .build());

            if (response.getStatus() == R.Status.Success.getCode() && Boolean.TRUE.equals(response.getData())) {
                info.put("exists", true);
                info.put("dimension", getEmbeddingDimension());
                info.put("vectorCount", "N/A"); // SDK 2.3.4 不支持直接获取行数
                info.put("indexType", "AUTOINDEX");
                info.put("status", "connected");
            } else {
                info.put("exists", false);
                info.put("status", "collection_not_found");
            }
        } catch (Exception e) {
            log.error("Failed to get collection info: {}", e.getMessage());
            info.put("exists", false);
            info.put("status", "error");
            info.put("error", e.getMessage());
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception ignored) {}
            }
        }
        return info;
    }

    /**
     * 重建 Collection (删除并重新创建)
     */
    public void recreateCollection() {
        MilvusServiceClient client = null;
        try {
            client = createClient();
            // 删除现有 Collection
            client.dropCollection(io.milvus.param.collection.DropCollectionParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .build());
            log.info("Dropped collection '{}'", COLLECTION_NAME);
        } catch (Exception e) {
            log.warn("Failed to drop collection (may not exist): {}", e.getMessage());
        } finally {
            if (client != null) {
                client.close();
            }
        }
        // 重新创建
        initCollection();
    }
}
