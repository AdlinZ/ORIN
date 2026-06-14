"""
AI Engine 测试全局 fixture。

关键约定：
- **OTel SDK 默认 disable**（`OTEL_SDK_DISABLED=true`）—— 防止 BatchSpanProcessor
  后台线程污染现有 trace_* 测试、避免对 OTLP endpoint 真实发请求、避免
  `tests/` 偶现 `ResourceWarning` / `RuntimeError: generator ignored GeneratorExit`。
  需要测 OTel 路径的子集测试（test_otel_*.py）显式 unsetenv，并自己管 init 时机。
- `trace_context` contextvar 在每个测试前后清空 —— 与 `test_logging_filter` /
  `test_trace_context` 等已存在测试行为一致。
- `_ORIN_OTEL_INITIALIZED` 模块级标志在每个测试间复位 —— 让 init 路径可重复
  触发（标志层）；但 OTel global TracerProvider **不可 reset**（1.42.x 第二次
  set 会破坏状态），所以 test_otel_setup.py 内的真 init 走"全 file 内只一次
  lifecycle"策略。
"""
from __future__ import annotations

import os

# 必须在所有 import 之前 setenv —— pytest 收集阶段就生效。
# 但要保留子集测试（test_otel_*.py）能显式 unsetenv 测 enable 路径。
os.environ.setdefault("OTEL_SDK_DISABLED", "true")

import pytest  # noqa: E402

from app.core import trace_context  # noqa: E402


@pytest.fixture(autouse=True)
def _reset_trace_context() -> None:
    """每个测试前后清空 trace_context contextvar + 重置 OTel 守门标志。"""
    import app.core.otel_setup as otel_setup_mod
    otel_setup_mod._ORIN_OTEL_INITIALIZED = False
    trace_context.clear()
    yield
    otel_setup_mod._ORIN_OTEL_INITIALIZED = False
    trace_context.clear()
