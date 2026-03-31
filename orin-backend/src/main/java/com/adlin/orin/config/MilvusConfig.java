package com.adlin.orin.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.RetryParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class MilvusConfig {

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

    @Bean
    public MilvusServiceClient milvusServiceClient() {
        log.info("Connecting to Milvus at {}:{}", host, port);
        ConnectParam.Builder builder = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .withConnectTimeout(connectTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .withKeepAliveTimeout(keepAliveTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .withRpcDeadline(rpcDeadlineMs, java.util.concurrent.TimeUnit.MILLISECONDS);

        if (token != null && !token.isEmpty()) {
            builder.withToken(token);
        }

        MilvusServiceClient client = new MilvusServiceClient(builder.build());
        RetryParam retryParam = RetryParam.newBuilder()
                .withMaxRetryTimes(Math.max(0, maxRetryTimes))
                .withInitialBackOffMs(Math.max(1, retryIntervalMs))
                .withMaxBackOffMs(Math.max(1, retryIntervalMs))
                .build();
        return (MilvusServiceClient) client.withRetry(retryParam);
    }
}
