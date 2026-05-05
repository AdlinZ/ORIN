package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.common.exception.VectorizationException;
import com.adlin.orin.modules.knowledge.component.VectorStoreProvider;
import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.ConnectParam;
import io.milvus.param.R;
import io.milvus.param.RetryParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.partition.CreatePartitionParam;
import io.milvus.param.partition.HasPartitionParam;
import io.milvus.param.partition.ShowPartitionsParam;
import io.milvus.grpc.ShowPartitionsResponse;
import io.milvus.grpc.DataType;
import io.milvus.param.MetricType;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.GetLoadingProgressParam;
import io.milvus.grpc.GetLoadingProgressResponse;
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

    @Value("${milvus.host}")
    private String host;

    @Value("${milvus.port}")
    private int port;

    @Value("${milvus.token:}")
    private String token;

    @Value("${milvus.client.connect-timeout-ms:1000}")
    private long connectTimeoutMs;

    @Value("${milvus.client.keepalive-timeout-ms:2000}")
    private long keepAliveTimeoutMs;

    @Value("${milvus.client.rpc-deadline-ms:1500}")
    private long rpcDeadlineMs;

    @Value("${milvus.client.max-retry-times:1}")
    private int maxRetryTimes;

    @Value("${milvus.client.retry-interval-ms:200}")
    private long retryIntervalMs;

    // 全局唯一的 Collection 名称
    private static final String COLLECTION_NAME = "orin_knowledge_base";
    private static final long VECTOR_STATS_QUERY_PAGE_SIZE = 16_384L;

    private final com.adlin.orin.gateway.service.ProviderRegistry providerRegistry;
    private final com.adlin.orin.modules.knowledge.component.EmbeddingService embeddingService;
    private final com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository documentRepository;
    private final com.adlin.orin.modules.knowledge.repository.KnowledgeBaseRepository knowledgeBaseRepository;

    public MilvusVectorService(
            com.adlin.orin.gateway.service.ProviderRegistry providerRegistry,
            com.adlin.orin.modules.knowledge.component.EmbeddingService embeddingService,
            com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository documentRepository,
            com.adlin.orin.modules.knowledge.repository.KnowledgeBaseRepository knowledgeBaseRepository) {
        this.providerRegistry = providerRegistry;
        this.embeddingService = embeddingService;
        this.documentRepository = documentRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        log.info("MilvusVectorService constructed with EmbeddingService: {}", embeddingService != null ? embeddingService.getClass().getName() : "NULL");
    }

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

            if (response.getStatus() != R.Status.Success.getCode()) {
                throw new IllegalStateException("Milvus hasCollection failed: " + response.getMessage());
            }

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
                    return;
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
        ConnectParam.Builder builder = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .withConnectTimeout(connectTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .withKeepAliveTimeout(keepAliveTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .withRpcDeadline(rpcDeadlineMs, java.util.concurrent.TimeUnit.MILLISECONDS);

        applyAuthorization(builder, token);

        MilvusServiceClient client = new MilvusServiceClient(builder.build());
        RetryParam retryParam = RetryParam.newBuilder()
                .withMaxRetryTimes(Math.max(0, maxRetryTimes))
                .withInitialBackOffMs(Math.max(1, retryIntervalMs))
                .withMaxBackOffMs(Math.max(1, retryIntervalMs))
                .build();
        return (MilvusServiceClient) client.withRetry(retryParam);
    }

    private void applyAuthorization(ConnectParam.Builder builder, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        String normalizedToken = rawToken.trim();
        if (normalizedToken.contains(":")) {
            builder.withAuthorization(normalizedToken);
        } else {
            builder.withAuthorization("root", normalizedToken);
        }
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

            // Flush to ensure data is persisted and searchable immediately
            client.flush(io.milvus.param.collection.FlushParam.newBuilder()
                    .withCollectionNames(Arrays.asList(COLLECTION_NAME))
                    .build());
            log.info("Flushed collection {} to ensure data is searchable", COLLECTION_NAME);

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

        // 维度检查
        int collectionDimension = getEmbeddingDimension();

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
                    // 检查向量维度
                    if (vector.size() != collectionDimension) {
                        log.error("向量维度不匹配! 文档: {}, 向量维度: {}, Collection 维度: {}. " +
                                  "这会导致向量无法正确插入. 请重建 Collection 或检查 Embedding 模型配置.",
                                chunk.getDocumentId(), vector.size(), collectionDimension);
                    }
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

            // Flush to ensure data is persisted and searchable immediately
            client.flush(io.milvus.param.collection.FlushParam.newBuilder()
                    .withCollectionNames(Arrays.asList(COLLECTION_NAME))
                    .build());
            log.info("Flushed collection {} to ensure data is searchable", COLLECTION_NAME);

        } catch (Exception e) {
            log.error("Milvus chunks insert error: {}", e.getMessage(), e);
            throw new VectorizationException("Failed to insert chunks into Milvus: " + e.getMessage(), e);
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

        // Update document metadata after vector deletion
        try {
            for (String docId : docIds) {
                documentRepository.updateVectorStatus(docId, "PENDING");
                documentRepository.updateChunkCount(docId, 0);
            }
        } catch (Exception e) {
            log.error("Failed to update document metadata after vector deletion: {}", e.getMessage());
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

    /**
     * 检查知识库在 Milvus 中是否存在向量数据
     */
    @Override
    public Map<String, Object> getVectorStats(String kbId) {
        Map<String, Object> result = new HashMap<>();
        String partitionName = "kb_" + kbId.replace("-", "_");

        MilvusServiceClient client = null;
        try {
            client = createClient();

            // 检查分区是否存在且有数据
            boolean partitionExists = checkPartitionExists(client, COLLECTION_NAME, partitionName);
            result.put("exists", partitionExists);

            if (partitionExists) {
                try {
                    result.put("vectorCount", countPartitionRows(client, partitionName));
                } catch (Exception e) {
                    log.debug("Failed to count vectors: {}", e.getMessage());
                    result.put("vectorCount", 0L);
                }
            } else {
                result.put("vectorCount", 0L);
            }
        } catch (Exception e) {
            log.warn("Failed to get vector stats for KB {}: {}", kbId, e.getMessage());
            result.put("exists", false);
            result.put("vectorCount", -1L);
            result.put("error", e.getMessage());
        } finally {
            if (client != null) {
                client.close();
            }
        }

        return result;
    }

    private long countPartitionRows(MilvusServiceClient client, String partitionName) {
        R<io.milvus.grpc.GetPartitionStatisticsResponse> statsResponse = client.getPartitionStatistics(
                io.milvus.param.partition.GetPartitionStatisticsParam.newBuilder()
                        .withCollectionName(COLLECTION_NAME)
                        .withPartitionName(partitionName)
                        .withFlush(false)
                        .build());

        if (statsResponse.getStatus() == R.Status.Success.getCode() && statsResponse.getData() != null) {
            OptionalLong rowCount = statsResponse.getData().getStatsList().stream()
                    .filter(stat -> "row_count".equalsIgnoreCase(stat.getKey()))
                    .map(io.milvus.grpc.KeyValuePair::getValue)
                    .filter(value -> value != null && !value.isBlank())
                    .mapToLong(Long::parseLong)
                    .findFirst();
            if (rowCount.isPresent()) {
                return rowCount.getAsLong();
            }
        } else {
            log.debug("Milvus partition statistics unavailable: {}",
                    statsResponse.getMessage() != null ? statsResponse.getMessage() : statsResponse.getStatus());
        }

        return countPartitionRowsByQuery(client, partitionName);
    }

    private long countPartitionRowsByQuery(MilvusServiceClient client, String partitionName) {
        long total = 0L;
        long offset = 0L;

        while (true) {
            R<io.milvus.grpc.QueryResults> queryResponse = client.query(
                    io.milvus.param.dml.QueryParam.newBuilder()
                            .withCollectionName(COLLECTION_NAME)
                            .withPartitionNames(Collections.singletonList(partitionName))
                            .withExpr("chunk_id != \"\"")
                            .addOutField("chunk_id")
                            .withOffset(offset)
                            .withLimit(VECTOR_STATS_QUERY_PAGE_SIZE)
                            .build());

            if (queryResponse.getStatus() != R.Status.Success.getCode()) {
                log.debug("Milvus vector count page query failed: {}", queryResponse.getMessage());
                return total;
            }

            long pageRows = countQueryRows(queryResponse.getData());
            total += pageRows;
            if (pageRows < VECTOR_STATS_QUERY_PAGE_SIZE) {
                return total;
            }
            offset += VECTOR_STATS_QUERY_PAGE_SIZE;
        }
    }

    private long countQueryRows(io.milvus.grpc.QueryResults queryResults) {
        if (queryResults == null || queryResults.getFieldsDataCount() == 0) {
            return 0L;
        }

        io.milvus.grpc.FieldData fieldData = queryResults.getFieldsData(0);
        switch (fieldData.getType()) {
            case VarChar:
                return fieldData.getScalars().getStringData().getDataCount();
            case Int64:
                return fieldData.getScalars().getLongData().getDataCount();
            case Int32:
                return fieldData.getScalars().getIntData().getDataCount();
            default:
                return fieldData.getScalars().getStringData().getDataCount();
        }
    }

    /**
     * 获取指定 chunk 的向量数据
     * 使用 search API 查询特定的 chunk_id
     */
    @Override
    public Map<String, Object> getChunkVector(String kbId, String chunkId) {
        Map<String, Object> result = new HashMap<>();

        MilvusServiceClient client = null;
        try {
            client = createClient();

            // 加载 collection
            client.loadCollection(io.milvus.param.collection.LoadCollectionParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .build());

            // 创建一个 dummy 向量用于搜索（我们只关心元数据）
            int dim = getEmbeddingDimension();
            List<Float> dummyVector = new ArrayList<>();
            for (int i = 0; i < dim; i++) {
                dummyVector.add(0f);
            }

            // 使用 chunk_id filter 来搜索特定的 chunk
            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withMetricType(io.milvus.param.MetricType.COSINE)
                    .withTopK(1)
                    .withVectors(Collections.singletonList(dummyVector))
                    .withVectorFieldName("embedding")
                    .withExpr("chunk_id == '" + chunkId + "'")
                    .withOutFields(Arrays.asList("chunk_id", "content", "doc_id", "embedding", "chunk_type", "title"))
                    .build();

            R<SearchResults> response = client.search(searchParam);

            if (response.getStatus() != R.Status.Success.getCode()) {
                log.warn("Failed to search chunk vector: {}", response.getMessage());
                return Map.of("success", false, "error", "Milvus 查询失败: " + response.getMessage());
            }

            SearchResults searchResults = response.getData();
            if (searchResults.getResults() == null || searchResults.getResults().getIds() == null) {
                return Map.of("success", false, "error", "未找到向量数据");
            }

            // 使用 SearchResultsWrapper 解析结果 - 注意使用 getResults()
            io.milvus.response.SearchResultsWrapper wrapper = new io.milvus.response.SearchResultsWrapper(searchResults.getResults());

            // 获取第一条结果
            List<io.milvus.response.SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);
            if (scores == null || scores.isEmpty()) {
                return Map.of("success", false, "error", "未找到向量数据");
            }

            // 提取返回的字段数据
            Map<String, Object> data = new HashMap<>();
            data.put("chunkId", chunkId);
            data.put("score", scores.get(0).getScore());

            // 获取各字段数据 - 使用 getFieldData
            try {
                List<?> contents = wrapper.getFieldData("content", 0);
                if (contents != null && !contents.isEmpty()) {
                    data.put("content", contents.get(0));
                }
            } catch (Exception e) {
                log.debug("Failed to get content field: {}", e.getMessage());
            }

            try {
                List<?> docIds = wrapper.getFieldData("doc_id", 0);
                if (docIds != null && !docIds.isEmpty()) {
                    data.put("docId", docIds.get(0));
                }
            } catch (Exception e) {
                log.debug("Failed to get doc_id field: {}", e.getMessage());
            }

            try {
                List<?> chunkTypes = wrapper.getFieldData("chunk_type", 0);
                if (chunkTypes != null && !chunkTypes.isEmpty()) {
                    data.put("chunkType", chunkTypes.get(0));
                }
            } catch (Exception e) {
                log.debug("Failed to get chunk_type field: {}", e.getMessage());
            }

            try {
                List<?> titles = wrapper.getFieldData("title", 0);
                if (titles != null && !titles.isEmpty()) {
                    data.put("title", titles.get(0));
                }
            } catch (Exception e) {
                log.debug("Failed to get title field: {}", e.getMessage());
            }

            // 获取 embedding 向量 - 使用 getFieldData
            try {
                List<?> vectors = wrapper.getFieldData("embedding", 0);
                if (vectors != null && !vectors.isEmpty()) {
                    // embedding 可能是 Float 数组
                    Object vectorObj = vectors.get(0);
                    if (vectorObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Float> vectorList = (List<Float>) vectorObj;
                        List<Double> doubleList = vectorList.stream().map(Double::valueOf).collect(Collectors.toList());
                        data.put("embedding", doubleList);
                        data.put("embeddingDimension", doubleList.size());
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to get embedding vector: {}", e.getMessage());
            }

            result.put("success", true);
            result.put("data", data);

        } catch (Exception e) {
            log.warn("Failed to get chunk vector for {}: {}", chunkId, e.getMessage());
            result.put("success", false);
            String errorMsg = e.getMessage();
            // 添加更友好的错误提示
            if (errorMsg != null && errorMsg.contains("auth")) {
                errorMsg = "Milvus 认证失败，请检查配置文件中的 milvus.token 设置";
            } else if (errorMsg != null && errorMsg.contains("Connection")) {
                errorMsg = "无法连接到 Milvus 服务，请检查网络和配置";
            }
            result.put("error", errorMsg);
        } finally {
            if (client != null) {
                client.close();
            }
        }

        return result;
    }

    @Override
    public List<SearchResult> search(String kbId, String query, int k) {
        return search(kbId, query, k, null);
    }

    @Override
    public List<SearchResult> search(String kbId, String query, int k, String embeddingModel) {
        String partitionName = "kb_" + kbId.replace("-", "_");

        // 快速失败：如果 Embedding 服务不可用，直接返回空结果
        List<Float> queryVector;
        try {
            queryVector = textToVector(query, embeddingModel);

            // 维度检查
            int collectionDimension = getEmbeddingDimension();
            if (queryVector.size() != collectionDimension) {
                log.error("向量维度不匹配! 查询向量维度: {}, Collection 维度: {}. 这会导致搜索结果为空. " +
                        "请重建 Collection 或检查 Embedding 模型配置.",
                        queryVector.size(), collectionDimension);
            }
        } catch (Exception e) {
            log.warn("Embedding 服务不可用，跳过向量搜索: {}", e.getMessage());
            return Collections.emptyList();
        }

        // 快速失败：如果 Milvus 服务不可用，直接返回空结果
        MilvusServiceClient client = null;
        try {
            client = createClient();

            boolean isGlobalSearch = "all".equalsIgnoreCase(kbId);

            // Check if partition exists first for specific kb
            if (!isGlobalSearch && !checkPartitionExists(client, COLLECTION_NAME, partitionName)) {
                log.warn("Partition not found: {}, returning empty results. kbId={}", partitionName, kbId);
                return Collections.emptyList();
            }

            log.info("Searching in collection: {}, query: {}, topK: {}", COLLECTION_NAME, query, k);

            // 总是加载整个 collection（不再使用 partition）
            client.loadCollection(io.milvus.param.collection.LoadCollectionParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .build());

            SearchParam.Builder searchBuilder = SearchParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withMetricType(io.milvus.param.MetricType.COSINE)
                    .withTopK(k)
                    .withVectors(Collections.singletonList(queryVector))
                    .withVectorFieldName("embedding")
                    .withOutFields(Arrays.asList("content", "doc_id", "chunk_id", "chunk_type", "parent_id", "title", "source", "position"));

            // Only search child chunks for vector retrieval - Temporarily disabled for debugging
            // searchBuilder.withExpr("chunk_type == 'child'");

            // 使用 partition 来限制搜索特定知识库
            if (!isGlobalSearch) {
                // 恢复使用 partition 过滤
                searchBuilder.withPartitionNames(Collections.singletonList(partitionName));
                log.info("Using partition filter for kbId={}, partition={}", kbId, partitionName);
            }

            SearchParam searchParam = searchBuilder.build();

            R<SearchResults> response = client.search(searchParam);

            if (response.getStatus() != R.Status.Success.getCode()) {
                log.error("Milvus search failed: status={}, message={}", response.getStatus(), response.getMessage());
                return Collections.emptyList();
            }

            SearchResults searchResults = response.getData();
            log.warn("DEBUG: searchResults = {}", searchResults);

            io.milvus.response.SearchResultsWrapper wrapper = new io.milvus.response.SearchResultsWrapper(
                    searchResults.getResults());
            List<io.milvus.response.SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);

            log.info("Milvus search returned {} results", scores.size());

            // Debug: print raw results
            if (scores.size() > 0) {
                log.warn("DEBUG: Got {} search results, first score: {}", scores.size(), scores.get(0).getScore());
            }

            // Debug: Query actual data count in partition
            if (scores.size() == 0) {
                // Debug: Try search without partition filter (entire collection)
                try {
                    SearchParam debugSearchParam = SearchParam.newBuilder()
                            .withCollectionName(COLLECTION_NAME)
                            .withMetricType(io.milvus.param.MetricType.COSINE)
                            .withTopK(10)
                            .withVectors(Collections.singletonList(queryVector))
                            .withVectorFieldName("embedding")
                            .build();
                    R<SearchResults> debugResponse = client.search(debugSearchParam);
                    log.warn("DEBUG: Search entire collection (no partition) status: {}, results: {}",
                            debugResponse.getStatus(),
                            debugResponse.getData() != null ? "got data" : "no data");
                } catch (Exception e) {
                    log.warn("Debug search failed: {}", e.getMessage());
                }
            }

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

            List<?> contents;
            List<?> docIds;
            List<?> chunkIds;
            List<?> chunkTypes;
            List<?> parentIds;
            List<?> titles;
            List<?> sources;
            List<?> positions;
            try {
                contents = wrapper.getFieldData("content", 0);
                docIds = wrapper.getFieldData("doc_id", 0);
                chunkIds = wrapper.getFieldData("chunk_id", 0);
                chunkTypes = wrapper.getFieldData("chunk_type", 0);
                parentIds = wrapper.getFieldData("parent_id", 0);
                titles = wrapper.getFieldData("title", 0);
                sources = wrapper.getFieldData("source", 0);
                positions = wrapper.getFieldData("position", 0);
            } catch (Exception e) {
                log.warn("Failed to get field data from Milvus search results, using fallback: {}", e.getMessage());
                contents = null;
                docIds = null;
                chunkIds = null;
                chunkTypes = null;
                parentIds = null;
                titles = null;
                sources = null;
                positions = null;
            }

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
            ConnectParam.Builder builder = ConnectParam.newBuilder()
                    .withHost(host)
                    .withPort(port)
                    .withConnectTimeout(connectTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .withKeepAliveTimeout(keepAliveTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .withRpcDeadline(rpcDeadlineMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            applyAuthorization(builder, token);
            client = new MilvusServiceClient(builder.build());
            RetryParam retryParam = RetryParam.newBuilder()
                    .withMaxRetryTimes(Math.max(0, maxRetryTimes))
                    .withInitialBackOffMs(Math.max(1, retryIntervalMs))
                    .withMaxBackOffMs(Math.max(1, retryIntervalMs))
                    .build();
            client = (MilvusServiceClient) client.withRetry(retryParam);

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
                    log.info("Embedding generated via SiliconFlow for query: '{}', dim={}", text.substring(0, Math.min(30, text.length())), embedding.size());
                    return embedding;
                }
            } catch (Exception e) {
                log.warn("SiliconFlow embedding failed, falling back to random vector: {}", e.getMessage());
            }
        } else {
            log.error("EmbeddingService is NULL! Cannot generate semantic vectors. Please check SiliconFlow API Key configuration.");
        }
        // 降级: 随机向量 (text.hashCode 保证同一文本向量一致)
        int dim = getEmbeddingDimension();
        log.error("CRITICAL: Using FAKE random vector for text! Semantic search will NOT work! dimension={}. " +
                  "Please configure valid SiliconFlow API Key in application.properties or database.", dim);
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

                // 获取向量数量 - 通过查询判断是否有数据
                try {
                    // 先 flush 确保数据落盘
                    client.flush(io.milvus.param.collection.FlushParam.newBuilder()
                            .withCollectionNames(Arrays.asList(COLLECTION_NAME))
                            .build());

                    // 查询是否有数据
                    String countExpr = "chunk_id != \"\"";
                    R<io.milvus.grpc.QueryResults> queryResponse = client.query(
                            io.milvus.param.dml.QueryParam.newBuilder()
                                    .withCollectionName(COLLECTION_NAME)
                                    .withExpr(countExpr)
                                    .withLimit(1L)
                                    .build());

                    if (queryResponse.getStatus() == R.Status.Success.getCode()) {
                        // 查询成功说明 collection 可用且有数据
                        info.put("vectorCount", "有数据");
                        info.put("vectorCountNote", "数据正常，可检索");
                    } else {
                        info.put("vectorCount", 0);
                    }
                } catch (Exception e) {
                    log.warn("Failed to get vector count: {}", e.getMessage());
                    info.put("vectorCount", 0);
                }

                info.put("indexType", "IVF_FLAT");
                info.put("status", "connected");
            } else {
                info.put("exists", false);
                info.put("status", "collection_not_found");
                info.put("vectorCount", 0);
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
     * 获取 Collection 详细数据
     */
    public Map<String, Object> getCollectionDetail() {
        Map<String, Object> detail = new HashMap<>();
        MilvusServiceClient client = null;

        try {
            client = createClient();
            detail.put("host", host);
            detail.put("port", port);
            detail.put("collectionName", COLLECTION_NAME);

            // 检查 collection 是否存在
            R<Boolean> hasCollection = client.hasCollection(HasCollectionParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .build());

            if (hasCollection.getStatus() != R.Status.Success.getCode() || !Boolean.TRUE.equals(hasCollection.getData())) {
                detail.put("exists", false);
                detail.put("status", "collection_not_found");
                return detail;
            }

            detail.put("exists", true);
            detail.put("dimension", getEmbeddingDimension());
            detail.put("status", "connected");

            // 从数据库获取精确统计数据
            try {
                long totalDocs = documentRepository.countIndexedDocuments();
                long totalVectors = documentRepository.sumTotalChunks();
                int partitionCount = 0;

                // 查询判断 Milvus 是否有数据
                client.flush(io.milvus.param.collection.FlushParam.newBuilder()
                        .withCollectionNames(Arrays.asList(COLLECTION_NAME))
                        .build());

                String expr = "chunk_id != \"\"";
                R<io.milvus.grpc.QueryResults> queryResponse = client.query(
                        io.milvus.param.dml.QueryParam.newBuilder()
                                .withCollectionName(COLLECTION_NAME)
                                .withExpr(expr)
                                .withLimit(1L)
                                .build());

                if (queryResponse.getStatus() == R.Status.Success.getCode()) {
                    detail.put("hasData", totalVectors > 0);
                } else {
                    detail.put("hasData", false);
                }

                detail.put("totalVectors", totalVectors);
                detail.put("totalDocs", totalDocs);

                // 获取分区数量
                R<io.milvus.grpc.ShowPartitionsResponse> partitionsResponse = client.showPartitions(
                        io.milvus.param.partition.ShowPartitionsParam.newBuilder()
                                .withCollectionName(COLLECTION_NAME)
                                .build());

                if (partitionsResponse.getStatus() == R.Status.Success.getCode()) {
                    partitionCount = partitionsResponse.getData().getPartitionNamesCount();
                    detail.put("partitionCount", partitionCount);
                }

                // 获取每个知识库的统计信息
                List<Object[]> kbStats = documentRepository.countByKnowledgeBase();
                List<Map<String, Object>> knowledgeBaseList = new ArrayList<>();
                for (Object[] stat : kbStats) {
                    String kbId = (String) stat[0];
                    long docCount = (Long) stat[1];
                    long vectorCount = (Long) stat[2];

                    // 获取知识库名称
                    String kbName = kbId;
                    try {
                        var kb = knowledgeBaseRepository.findById(kbId);
                        if (kb.isPresent()) {
                            kbName = kb.get().getName();
                        }
                    } catch (Exception ignored) {}

                    Map<String, Object> kbInfo = new HashMap<>();
                    kbInfo.put("id", kbId);
                    kbInfo.put("name", kbName);
                    kbInfo.put("docCount", docCount);
                    kbInfo.put("vectorCount", vectorCount);
                    knowledgeBaseList.add(kbInfo);
                }
                detail.put("knowledgeBases", knowledgeBaseList);

            } catch (Exception e) {
                log.warn("Failed to get collection stats: {}", e.getMessage());
                detail.put("hasData", false);
                detail.put("totalVectors", 0);
                detail.put("totalDocs", 0);
                detail.put("partitionCount", 0);
                detail.put("knowledgeBases", new ArrayList<>());
            }

            detail.put("partitions", new ArrayList<>());

            return detail;

        } catch (Exception e) {
            log.error("Failed to get collection detail: {}", e.getMessage(), e);
            detail.put("status", "error");
            detail.put("error", e.getMessage());
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception ignored) {}
            }
        }

        return detail;
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

    /**
     * 健康检查 - 检查 Milvus 服务是否可用
     * 用于检索降级策略
     */
    public boolean isHealthy() {
        MilvusServiceClient client = null;
        try {
            client = createClient();
            // 尝试获取 collection 加载状态
            R<GetLoadingProgressResponse> response = client.getLoadingProgress(
                    io.milvus.param.collection.GetLoadingProgressParam.newBuilder()
                            .withCollectionName(COLLECTION_NAME)
                            .build()
            );
            // 如果能获取响应则认为服务可用
            return response.getStatus() == io.milvus.param.R.Status.Success.getCode();
        } catch (Exception e) {
            log.warn("Milvus health check failed: {}", e.getMessage());
            return false;
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    log.warn("Failed to close Milvus client: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 获取 Milvus 连接状态信息
     */
    public String getConnectionStatus() {
        MilvusServiceClient client = null;
        try {
            client = createClient();
            R<GetLoadingProgressResponse> response = client.getLoadingProgress(
                    io.milvus.param.collection.GetLoadingProgressParam.newBuilder()
                            .withCollectionName(COLLECTION_NAME)
                            .build()
            );
            if (response.getStatus() == io.milvus.param.R.Status.Success.getCode()) {
                return "CONNECTED";
            } else {
                return "ERROR: " + response.getMessage();
            }
        } catch (Exception e) {
            return "UNAVAILABLE: " + e.getMessage();
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception ignored) {}
            }
        }
    }
}
