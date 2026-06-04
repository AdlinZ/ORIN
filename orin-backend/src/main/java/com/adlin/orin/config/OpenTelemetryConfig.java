package com.adlin.orin.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * OpenTelemetry 配置
 *
 * 支持 OTLP gRPC 导出到 Jaeger / Tempo / 其他 OTel 兼容后端。
 * 通过环境变量配置（本地 dev 默认关闭，生产按需启用）：
 *
 *   ORIN_TRACING_ENABLED=true          — 显式启用开关
 *   OTEL_EXPORTER_OTLP_ENDPOINT        — OTLP 接收端，如 http://localhost:4317
 *   OTEL_SERVICE_NAME                  — 服务名，默认 orin-backend
 *
 * AI Engine（Python）侧需独立配置 jaeger-client-python 或 OTLP Python exporter。
 */
@Slf4j
@Configuration
public class OpenTelemetryConfig {

    @Value("${otel.exporter.otlp.endpoint:${OTEL_EXPORTER_OTLP_ENDPOINT:}}")
    private String otlpEndpoint;

    @Value("${otel.service.name:${OTEL_SERVICE_NAME:orin-backend}}")
    private String serviceName;

    @Value("${otel.tracing.enabled:${ORIN_TRACING_ENABLED:false}}")
    private boolean tracingEnabled;

    /**
     * SpanExporter — 仅在 tracing 启用且 endpoint 已配置时创建。
     * 否则 tracing 启用但无 exporter 时使用 no-op span processor。
     */
    @Bean
    @ConditionalOnProperty(name = "otel.tracing.enabled", havingValue = "true", matchIfMissing = false)
    public SpanExporter spanExporter() {
        if (otlpEndpoint == null || otlpEndpoint.isBlank()) {
            log.warn("OTEL_EXPORTER_OTLP_ENDPOINT not set — traces will be dropped (no exporter)");
            return SpanExporter.composite(); // empty composite = no-op
        }
        log.info("Configuring OTLP gRPC span exporter endpoint: {}", otlpEndpoint);
        return OtlpGrpcSpanExporter.builder()
                .setEndpoint(otlpEndpoint)
                .setTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    /**
     * SdkTracerProvider — 启用 tracing 时配置真正的 provider。
     * tracing 禁用时返回 no-op provider，避免 CollaborationTracer 等组件报错。
     */
    @Bean
    @ConditionalOnProperty(name = "otel.tracing.enabled", havingValue = "true", matchIfMissing = false)
    public SdkTracerProvider tracerProvider(SpanExporter spanExporter) {
        log.info("Initializing OTel SdkTracerProvider (service={}, endpoint={})", serviceName, otlpEndpoint);
        Resource resource = Resource.getDefault().merge(Resource.create(Attributes.of(
                io.opentelemetry.api.common.AttributeKey.stringKey("service.name"), serviceName
        )));

        var tracerProviderBuilder = SdkTracerProvider.builder()
                .setResource(resource);

        // Only add span processor if we have a real (non-noop) exporter
        if (spanExporter != null && !(spanExporter instanceof CompositeSpanExporter)) {
            tracerProviderBuilder.addSpanProcessor(BatchSpanProcessor.builder(spanExporter)
                    .setScheduleDelay(1, TimeUnit.SECONDS)
                    .setMaxQueueSize(2048)
                    .setMaxExportBatchSize(512)
                    .build());
        }

        return tracerProviderBuilder.build();
    }

    /**
     * No-op TracerProvider — tracing 未启用时使用。
     */
    @Bean
    @ConditionalOnProperty(name = "otel.tracing.enabled", havingValue = "false", matchIfMissing = true)
    public SdkTracerProvider noOpTracerProvider() {
        log.info("OpenTelemetry tracing is disabled — using no-op tracer provider");
        return SdkTracerProvider.builder()
                .setResource(Resource.getDefault())
                .build();
    }

    @Bean
    public OpenTelemetry openTelemetry(SdkTracerProvider tracerProvider) {
        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
    }

    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(serviceName, "1.0.0");
    }

    // Marker interface for empty composite detection
    private interface CompositeSpanExporter extends SpanExporter {}
}