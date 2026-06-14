"""
OTel SDK setup 单测（Phase 2 小刀 5a）。

OTel SDK 1.42.x 约束：进程级 TracerProvider 不可 reset（`set_tracer_provider`
第二次调用会破坏全局 `_TRACER_PROVIDER` 状态，导致 `get_tracer` 死循环）。

本测试因此把 init 路径拆为"独立 mock 分支"+"一个真 lifecycle 收口"：
- 大部分测试用 mock patch 隔离 setup_tracing 内部子路径（disabled / env 优先级 /
  no endpoint 走 Console / endpoint 走 OTLP / 幂等 / shutdown 安全 / 标志翻转）
- 唯一真 init 的 lifecycle 测试在**最后**跑一次（其它测试不污染 global provider）
- 真 init 之后所有依赖 OTel global state 的测试（get_tracer 真实调用）放在
  lifecycle 紧邻的 setup 之后
"""
from __future__ import annotations

import logging

import pytest

from app.core import otel_setup


# ---- disabled 模式（mock 隔离 OTel 内部）----


def test_setup_disabled_returns_false(monkeypatch) -> None:
    """OTEL_SDK_DISABLED=true：setup 不建 TracerProvider，返 False。"""
    monkeypatch.setenv("OTEL_SDK_DISABLED", "true")
    otel_setup._ORIN_OTEL_INITIALIZED = False

    ok = otel_setup.setup_tracing()

    assert ok is False
    assert otel_setup._ORIN_OTEL_INITIALIZED is False


@pytest.mark.parametrize("val", ["1", "true", "yes", "TRUE", "Yes"])
def test_is_disabled_parses_truthy_values(monkeypatch, val: str) -> None:
    monkeypatch.setenv("OTEL_SDK_DISABLED", val)
    assert otel_setup.is_disabled() is True


def test_is_disabled_false_when_unset(monkeypatch) -> None:
    monkeypatch.delenv("OTEL_SDK_DISABLED", raising=False)
    assert otel_setup.is_disabled() is False


# ---- env 优先级（不调 OTel，只读 env 解析函数）----


@pytest.mark.parametrize(
    "otel_svc, orin_svc, expected",
    [
        ("from-otel", "from-orin", "from-otel"),
        (None, "from-orin", "from-orin"),
        (None, None, "orin-ai-engine"),
        ("", "from-orin", "from-orin"),
    ],
)
def test_service_name_env_priority(monkeypatch, otel_svc, orin_svc, expected) -> None:
    if otel_svc is None:
        monkeypatch.delenv("OTEL_SERVICE_NAME", raising=False)
    else:
        monkeypatch.setenv("OTEL_SERVICE_NAME", otel_svc)
    if orin_svc is None:
        monkeypatch.delenv("ORIN_SERVICE_NAME", raising=False)
    else:
        monkeypatch.setenv("ORIN_SERVICE_NAME", orin_svc)
    assert otel_setup._service_name() == expected


def test_otlp_endpoint_returns_none_when_unset(monkeypatch) -> None:
    monkeypatch.delenv("OTEL_EXPORTER_OTLP_ENDPOINT", raising=False)
    monkeypatch.delenv("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT", raising=False)
    assert otel_setup._otlp_endpoint() is None


def test_otlp_endpoint_otel_takes_priority(monkeypatch) -> None:
    monkeypatch.setenv("OTEL_EXPORTER_OTLP_ENDPOINT", "http://otel:4318")
    monkeypatch.setenv("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT", "http://traces:4318")
    assert otel_setup._otlp_endpoint() == "http://otel:4318"


def test_otlp_endpoint_falls_back_to_traces_specific(monkeypatch) -> None:
    monkeypatch.delenv("OTEL_EXPORTER_OTLP_ENDPOINT", raising=False)
    monkeypatch.setenv("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT", "http://traces:4318")
    assert otel_setup._otlp_endpoint() == "http://traces:4318"


# ---- shutdown 安全（mock 隔离）----


def test_shutdown_safe_when_disabled(monkeypatch) -> None:
    """disabled 状态下 shutdown 立即 return。"""
    monkeypatch.setenv("OTEL_SDK_DISABLED", "true")
    otel_setup.shutdown_tracing()  # 不应抛


def test_shutdown_safe_when_not_initialized(monkeypatch) -> None:
    """未 init 时 shutdown 走 no-op 分支。"""
    monkeypatch.delenv("OTEL_SDK_DISABLED", raising=False)
    otel_setup._ORIN_OTEL_INITIALIZED = False
    otel_setup.shutdown_tracing()  # 不应抛


def test_shutdown_safe_when_otel_missing(monkeypatch) -> None:
    """包缺失时 shutdown 走 no-op 分支。"""
    monkeypatch.setattr(otel_setup, "_OTEL_AVAILABLE", False)
    otel_setup._ORIN_OTEL_INITIALIZED = True
    otel_setup.shutdown_tracing()  # 不应抛


# ---- 幂等 init（mock 内部 _init 路径）----


def test_idempotent_returns_false_when_flag_set(monkeypatch) -> None:
    """标志已 True 时第二次调用直接返 False，不进入 OTel SDK 路径。"""
    monkeypatch.delenv("OTEL_SDK_DISABLED", raising=False)
    otel_setup._ORIN_OTEL_INITIALIZED = True
    # 若意外进入 SDK 路径会破坏 global state —— 用 sentinel 触发 fail
    fail_marker = {"called": False}

    def _boom(*args, **kwargs):
        fail_marker["called"] = True
        raise AssertionError("should not enter OTel init path when flag set")

    monkeypatch.setattr(otel_setup, "TracerProvider", _boom)
    ok = otel_setup.setup_tracing()
    assert ok is False
    assert fail_marker["called"] is False


def test_idempotent_returns_false_when_existing_tracer_provider(monkeypatch) -> None:
    """type 检查分支：现有 global provider 已是 TracerProvider，跳过 set。"""
    monkeypatch.delenv("OTEL_SDK_DISABLED", raising=False)
    otel_setup._ORIN_OTEL_INITIALIZED = False

    # mock global provider 返真实 TracerProvider 类型（type name 是 "TracerProvider"）
    from opentelemetry.sdk.trace import TracerProvider
    fake_tp = TracerProvider()  # 真实例，type name = "TracerProvider"

    class _FakeTrace:
        def get_tracer_provider(self):
            return fake_tp

    monkeypatch.setattr(otel_setup, "trace", _FakeTrace())
    ok = otel_setup.setup_tracing()
    # 标志被置 True 但没真正 set 我们的新 TracerProvider
    assert ok is False
    assert otel_setup._ORIN_OTEL_INITIALIZED is True


# ---- 完整 lifecycle（**真 init 一次**）----


def test_full_lifecycle_init_console_then_shutdown(monkeypatch, caplog) -> None:
    """端到端：disabled 解开 + 无 endpoint → 真 init ConsoleSpanExporter → shutdown。

    这是本文件**唯一**真 init 一次。其它测试都用 mock 隔离，不污染 OTel
    global state。
    """
    monkeypatch.delenv("OTEL_SDK_DISABLED", raising=False)
    monkeypatch.delenv("OTEL_EXPORTER_OTLP_ENDPOINT", raising=False)
    monkeypatch.delenv("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT", raising=False)
    monkeypatch.setenv("OTEL_SERVICE_NAME", "orin-ai-engine-test")
    otel_setup._ORIN_OTEL_INITIALIZED = False

    caplog.set_level(logging.INFO, logger="app.core.otel_setup")
    with caplog.at_level(logging.INFO):
        ok = otel_setup.setup_tracing()
    assert ok is True
    assert otel_setup._ORIN_OTEL_INITIALIZED is True
    assert any("tracing initialized" in r.message for r in caplog.records)
    assert any("ConsoleSpanExporter" in r.message for r in caplog.records)
    assert any("service=orin-ai-engine-test" in r.message for r in caplog.records)
    assert any("sdk=1.42.1" in r.message for r in caplog.records)

    # shutdown 走 force_flush + shutdown
    otel_setup.shutdown_tracing()  # 不应抛


def test_lifecycle_get_tracer_returns_real_otlp_tracer(monkeypatch) -> None:
    """init 后 get_tracer 返 OTel 真实 Tracer。"""
    # 这条**不**调 setup_tracing（避免第二次 init 破坏 OTel state），
    # 直接验证：上一条 test lifecycle 留下的 global provider 是 TracerProvider。
    # pytest 顺序：test_full_lifecycle 跑过后 global 是真 TracerProvider。
    from opentelemetry import trace as otel_trace
    provider = otel_trace.get_tracer_provider()
    if type(provider).__name__ != "TracerProvider":
        pytest.skip("依赖 test_full_lifecycle_init_console_then_shutdown 先跑")
    tracer = otel_setup.get_tracer(__name__)
    with tracer.start_as_current_span("smoke") as span:
        sc = span.get_span_context()
        assert sc is not None
        assert sc.trace_id != 0
