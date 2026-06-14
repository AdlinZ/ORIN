"""
OTel SDK 初始化（Phase 2 小刀 5a 基础设施）。

设计要点（来自 workflow 评审共识）：
- 顶层 try-import：opentelemetry-* 包缺失时所有 public 函数降级为 no-op，不
  让 import 失败污染调用方（CI / 旧 venv / 纯单元测试环境）。
- 幂等 init：OTel SDK 1.42.x 的 `trace.set_tracer_provider` 是单例哨兵，重复
  set 会 raise `AlreadyProvisionedError`。本模块用模块级 `_ORIN_OTEL_INITIALIZED`
  守门 + `ProxyTracerProvider` 探测，uvicorn --reload 双 import 也安全。
- OTEL_SDK_DISABLED 兜底：环境变量 `OTEL_SDK_DISABLED=true` 走"完全 no-op"
  分支（连 TracerProvider 都不建），给 pytest conftest 用。
- endpoint 缺省降级：未设 `OTEL_EXPORTER_OTLP_ENDPOINT` 时用
  `ConsoleSpanExporter`（stdout），本地启动也能看到 span。
- env var 直读：`opentelemetry-*` 标准化 env var 不走 pydantic-settings
  （Settings.env_prefix=ORIN_ 拦截不到），用 `os.getenv` 显式读。
- 启停双钩子：`setup_tracing()` 在 main.py startup 调，`shutdown_tracing()`
  在 shutdown 调，强制 force_flush（防 K8s SIGTERM / batch processor 丢尾段）。
- 暴露 `get_tracer(name)`：业务模块 import 它拿 OTel Tracer，统一入口。
"""
from __future__ import annotations

import logging
import os
from typing import Any, Optional

logger = logging.getLogger(__name__)

# 模块级守门：uvicorn --reload 时 main.py 被 reimport，此标志阻止重复 set
_ORIN_OTEL_INITIALIZED: bool = False

# try-import 兜底：包缺失时所有 public 函数降级为 no-op
try:
    from opentelemetry import trace
    from opentelemetry.sdk.resources import Resource
    from opentelemetry.sdk.trace import TracerProvider
    from opentelemetry.sdk.trace.export import (
        BatchSpanProcessor,
        ConsoleSpanExporter,
    )
    from opentelemetry.exporter.otlp.proto.http.trace_exporter import (
        OTLPSpanExporter,
    )
    from opentelemetry.semconv.resource import ResourceAttributes
    _OTEL_AVAILABLE = True
except ImportError:  # pragma: no cover - 兜底分支
    _OTEL_AVAILABLE = False
    trace = None  # type: ignore[assignment]
    Resource = None  # type: ignore[assignment]
    TracerProvider = None  # type: ignore[assignment]
    BatchSpanProcessor = None  # type: ignore[assignment]
    ConsoleSpanExporter = None  # type: ignore[assignment]
    OTLPSpanExporter = None  # type: ignore[assignment]
    ResourceAttributes = None  # type: ignore[assignment]


def is_otel_available() -> bool:
    """OTel SDK 包是否成功 import。"""
    return _OTEL_AVAILABLE


def is_disabled() -> bool:
    """环境变量 `OTEL_SDK_DISABLED=true` 时直接 no-op。"""
    return os.getenv("OTEL_SDK_DISABLED", "").lower() in ("1", "true", "yes")


def _service_name() -> str:
    """`OTEL_SERVICE_NAME` env（OTel 规范），fallback `ORIN_SERVICE_NAME`，
    再 fallback `orin-ai-engine`。
    """
    return (
        os.getenv("OTEL_SERVICE_NAME")
        or os.getenv("ORIN_SERVICE_NAME")
        or "orin-ai-engine"
    )


def _deployment_environment() -> str:
    return os.getenv("DEPLOYMENT_ENVIRONMENT") or os.getenv("ORIN_ENV") or "dev"


def _otlp_endpoint() -> Optional[str]:
    """OTel 规范 env `OTEL_EXPORTER_OTLP_ENDPOINT` / `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT`。
    返回 None 表示未设 → 走 ConsoleSpanExporter（开发期 stdout）。
    """
    return (
        os.getenv("OTEL_EXPORTER_OTLP_ENDPOINT")
        or os.getenv("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT")
    )


def setup_tracing() -> bool:
    """初始化 TracerProvider + BatchSpanProcessor。

    返回 `True` 表示成功建了 provider；`False` 表示 disabled / 包缺失 / 已
    初始化过 / 初始化失败（异常吞掉，单进程重复调用安全）。

    幂等：第二次调用直接 return False，不抛 `AlreadyProvisionedError`。
    启动期 main.py 调用一次即可。
    """
    global _ORIN_OTEL_INITIALIZED

    if _ORIN_OTEL_INITIALIZED:
        return False
    if not _OTEL_AVAILABLE:
        logger.info("[otel] opentelemetry-* not installed; tracing disabled")
        return False
    if is_disabled():
        logger.info("[otel] OTEL_SDK_DISABLED=true; tracing disabled")
        return False

    # 探测全局：ProxyTracerProvider 是占位，TracerProvider 是已 init。
    # 注意：trace.get_tracer_provider() 始终非空（默认 ProxyTracerProvider），
    # 不能用"非空即已 init"判断，要比对具体类型。
    existing = trace.get_tracer_provider()
    if type(existing).__name__ == "TracerProvider":
        # 已被外部 init 过，标记并返回；不再 set 避免 raise
        _ORIN_OTEL_INITIALIZED = True
        logger.info(
            "[otel] TracerProvider already initialized by another module; "
            "skipping re-init (class=%s)", type(existing).__name__,
        )
        return False

    service_name = _service_name()
    endpoint = _otlp_endpoint()

    resource = Resource.create({
        ResourceAttributes.SERVICE_NAME: service_name,
        ResourceAttributes.DEPLOYMENT_ENVIRONMENT: _deployment_environment(),
    })
    provider = TracerProvider(resource=resource)

    # 选 exporter：endpoint 非空 → OTLPSpanExporter（HTTP），空 → ConsoleSpanExporter
    if endpoint:
        exporter = OTLPSpanExporter(
            endpoint=f"{endpoint.rstrip('/')}/v1/traces"
            if not endpoint.rstrip("/").endswith("/v1/traces")
            else endpoint,
            timeout=10,
        )
        exporter_name = f"OTLPSpanExporter({endpoint})"
    else:
        exporter = ConsoleSpanExporter()
        exporter_name = "ConsoleSpanExporter(stdout)"

    # 参数对齐后端 Java（路线图 L424：避免 batch 粒度不一致 → OTLP 接收端乱序）
    provider.add_span_processor(
        BatchSpanProcessor(
            exporter,
            schedule_delay_millis=1000,
            max_queue_size=2048,
            max_export_batch_size=512,
        )
    )

    try:
        trace.set_tracer_provider(provider)
    except Exception as exc:  # AlreadyProvisionedError 等兜底
        logger.warning("[otel] set_tracer_provider failed: %s; tracing may be no-op", exc)
        return False

    _ORIN_OTEL_INITIALIZED = True
    logger.info(
        "[otel] tracing initialized: service=%s env=%s exporter=%s sdk=%s",
        service_name,
        _deployment_environment(),
        exporter_name,
        "1.42.1",
    )
    return True


def shutdown_tracing() -> None:
    """强制 flush 队列里未导出的 span，再 shutdown provider。

    main.py shutdown 钩子调用。K8s rolling upgrade / SIGTERM 时不调这个
    会丢尾段 spans（OTel SDK 默认后台 schedule thread，进程退出不保证 join）。
    """
    if not _OTEL_AVAILABLE or not _ORIN_OTEL_INITIALIZED:
        return

    try:
        provider = trace.get_tracer_provider()
        if hasattr(provider, "force_flush"):
            provider.force_flush(timeout_millis=2000)  # type: ignore[attr-defined]
        if hasattr(provider, "shutdown"):
            provider.shutdown()  # type: ignore[attr-defined]
    except Exception as exc:  # pragma: no cover
        logger.warning("[otel] shutdown_tracing failed: %s", exc)


def get_tracer(name: str) -> Any:
    """业务模块拿 OTel Tracer 的统一入口。

    包缺失 / 未 init 时退化为 OTel 自带的 NoOpTracer（trace.get_tracer
    默认就是 NoOp），业务代码不会因 OTel 不可用而炸。

    用法：
        from app.core.otel_setup import get_tracer
        tracer = get_tracer(__name__)
        with tracer.start_as_current_span("do-work") as span:
            span.set_attribute("k", "v")
    """
    if not _OTEL_AVAILABLE:
        # 给调用方一个 stub，start_as_current_span / start_span 仍在
        # 不会抛错，但 span 不会被记录
        return _NoopTracer()
    return trace.get_tracer(name)


class _NoopTracer:
    """OTel 包缺失时的最小 stub —— 让业务代码可继续 import 但不实际记录。"""

    def start_as_current_span(self, *args: Any, **kwargs: Any) -> "_NoopSpan":
        from contextlib import nullcontext
        return nullcontext(_NoopSpan())

    def start_span(self, *args: Any, **kwargs: Any) -> "_NoopSpan":
        return _NoopSpan()


class _NoopSpan:
    def __enter__(self) -> "_NoopSpan":
        return self

    def __exit__(self, *args: Any) -> None:
        return None

    def end(self) -> None:
        return None

    def set_attribute(self, *args: Any, **kwargs: Any) -> None:
        return None

    def set_status(self, *args: Any, **kwargs: Any) -> None:
        return None

    def record_exception(self, *args: Any, **kwargs: Any) -> None:
        return None

    def add_event(self, *args: Any, **kwargs: Any) -> None:
        return None

    def get_span_context(self) -> Any:
        return None
