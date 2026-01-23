package com.adlin.orin.modules.knowledge.service;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.grpc.ShowCollectionsResponse;
import io.milvus.param.ConnectParam;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.collection.ShowCollectionsParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Milvus 向量数据库服务 - 简化版
 * 提供向量检索功能框架
 */
@Slf4j
@Service
public class MilvusVectorService {

    /**
     * 创建 Milvus 客户端连接
     */
    public MilvusServiceClient createClient(String host, int port, String token) {
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .withAuthorization("root", token != null ? token : "Milvus")
                .build();

        return new MilvusServiceClient(connectParam);
    }

    /**
     * 向量检索 - 简化版
     * 注意: 这是一个框架实现,实际使用时需要根据具体的 Milvus SDK 版本调整
     */
    @org.springframework.beans.factory.annotation.Autowired
    private com.adlin.orin.gateway.service.ProviderRegistry providerRegistry;

    /**
     * 向量检索 - 简化版
     * 注意: 这是一个框架实现,实际使用时需要根据具体的 Milvus SDK 版本调整
     */
    public List<Map<String, Object>> search(
            String host,
            int port,
            String token,
            String collectionName,
            List<Float> queryVector,
            int topK,
            double threshold) {

        log.info("Milvus search - collection: {}, topK: {}", collectionName, topK);

        MilvusServiceClient client = null;
        try {
            client = createClient(host, port, token);

            // 1. 加载集合
            client.loadCollection(LoadCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build());

            // 2. 执行搜索
            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withMetricType(io.milvus.param.MetricType.COSINE) // 默认使用余弦相似度
                    .withTopK(topK)
                    .withVectors(Collections.singletonList(queryVector))
                    .withVectorFieldName("embedding") // 假设向量字段名为 embedding，需根据实际情况调整
                    // .withParams("{\"nprobe\":10}")
                    .build();

            R<SearchResults> response = client.search(searchParam);

            if (response.getStatus() != R.Status.Success.getCode()) {
                log.error("Milvus search failed: {}", response.getMessage());
                return Collections.emptyList();
            }

            SearchResults searchResults = response.getData();
            io.milvus.response.SearchResultsWrapper wrapper = new io.milvus.response.SearchResultsWrapper(
                    searchResults.getResults());

            List<io.milvus.response.SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);
            List<Map<String, Object>> resultList = new ArrayList<>();

            for (io.milvus.response.SearchResultsWrapper.IDScore score : scores) {
                if (score.getScore() < threshold) {
                    continue;
                }

                Map<String, Object> map = new HashMap<>();
                map.put("id", score.getLongID()); // 假设是 Long ID
                map.put("score", score.getScore());
                // 如果有 output fields，可以在这里获取，目前仅通过 ID 关联
                resultList.add(map);
            }

            return resultList;

        } catch (Exception e) {
            log.error("Milvus search error: {}", e.getMessage(), e);
            return Collections.emptyList();
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    log.error("Error closing Milvus client: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 文本转向量 (需要集成嵌入模型)
     */
    public List<Float> textToVector(String text, String embeddingModel) {
        // 集成 ProviderRegistry 获取 Embedding 能力
        // 优先查找 OpenAI 类型的 Provider
        var providers = providerRegistry.getHealthyProviders();

        for (var provider : providers) {
            // 这里简单假设只有 openai 支持 embedding，或者 providerAdapter 有 capability check
            if ("openai".equals(provider.getProviderType())) {
                try {
                    com.adlin.orin.gateway.dto.EmbeddingResponse response = provider.embedding(
                            com.adlin.orin.gateway.dto.EmbeddingRequest.builder()
                                    .model(embeddingModel != null ? embeddingModel : "text-embedding-ada-002")
                                    .input(Collections.singletonList(text))
                                    .build())
                            .block(); // 阻塞获取结果

                    if (response != null && !response.getData().isEmpty()) {
                        // 将 Double 转换为 Float
                        List<Double> embedding = response.getData().get(0).getEmbedding();
                        List<Float> floatEmbedding = new ArrayList<>(embedding.size());
                        for (Double d : embedding) {
                            floatEmbedding.add(d.floatValue());
                        }
                        return floatEmbedding;
                    }
                } catch (Exception e) {
                    log.warn("Failed to get embedding from provider {}: {}", provider.getProviderName(),
                            e.getMessage());
                }
            }
        }

        log.warn("No suitable embedding provider found, returning random vector (fallback)");
        // Fallback: 模拟向量
        List<Float> vector = new ArrayList<>();
        Random random = new Random(text.hashCode());
        for (int i = 0; i < 768; i++) {
            vector.add(random.nextFloat());
        }
        return vector;
    }

    /**
     * 健康检查
     */
    public boolean healthCheck(String host, int port, String token) {
        MilvusServiceClient client = null;
        try {
            client = createClient(host, port, token);
            // 简单的连接测试
            R<ShowCollectionsResponse> response = client.showCollections(
                    ShowCollectionsParam.newBuilder().build());
            return response.getStatus() == 0;
        } catch (Exception e) {
            log.error("Milvus health check failed: {}", e.getMessage());
            return false;
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    log.error("Error closing Milvus client: {}", e.getMessage());
                }
            }
        }
    }
}
