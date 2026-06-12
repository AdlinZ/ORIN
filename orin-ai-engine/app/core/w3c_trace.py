"""
W3C Trace Context 编解码工具，集中放给 AI Engine 所有跨边界链路（HTTP / MQ / 异步）复用。

镜像后端 `orin-backend/.../common/trace/TraceContext.java` 的公共 API 1:1：
- 格式：00-<32hex>-<16hex>-01
- parse / build / is_valid / generate_trace_id / generate_span_id / build_from_current

与 `app/core/trace_context.py` 解耦：本模块只做格式与随机 ID 生成，
不接触 contextvars。未来切 OTel SDK 只动 storage。
"""
from __future__ import annotations

import re
import secrets
from dataclasses import dataclass
from typing import Optional

# ---- 常量 ----

#: W3C 标准 traceparent header 名。
TRACEPARENT_HEADER: str = "traceparent"

#: W3C traceparent 严格正则。版本固定 00，全小写 16 进制。
TRACEPARENT_REGEX = re.compile(r"^00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$")

#: 与后端 TraceIdFilter 同步的 MDC / log field key 名。
TRACE_ID_KEY: str = "traceId"
SPAN_ID_KEY: str = "spanId"

#: 当前唯一支持的版本（W3C 现行 spec）。其余版本（ff、未来版本）一律拒绝。
_VERSION: str = "00"
_FLAGS_SAMPLED: str = "01"  # bit0 = sampled, 0x01 = sampled


# ---- 数据类型 ----


@dataclass(frozen=True)
class Traceparent:
    """W3C traceparent 解析结果。trace_id=32 hex，span_id=16 hex，flags 形如 '01'."""

    trace_id: str
    span_id: str
    flags: str = _FLAGS_SAMPLED


# ---- 解析 / 构建 / 校验 ----


def is_valid(header: Optional[str]) -> bool:
    """校验字符串是否为合法 W3C traceparent。None、空、长度不对、非 hex 一律 False。"""
    if not header:
        return False
    return bool(TRACEPARENT_REGEX.match(header))


def parse_traceparent(header: Optional[str]) -> Optional[Traceparent]:
    """解析 W3C traceparent header。非法返回 None。

    格式固定：00-<32hex>-<16hex>-<2hex>。索引 0..1=version，3..34=traceId，
    36..51=spanId，53..54=flags。
    """
    if not is_valid(header):
        return None
    return Traceparent(
        trace_id=header[3:35],   # 32 chars
        span_id=header[36:52],   # 16 chars
        flags=header[53:55],
    )


def build_traceparent(trace_id: str, span_id: str, flags: str = _FLAGS_SAMPLED) -> str:
    """组装 W3C traceparent header 值。flags 默认 01（sampled）。"""
    return f"{_VERSION}-{trace_id}-{span_id}-{flags}"


# ---- ID 生成 ----


def generate_trace_id() -> str:
    """32-char lowercase hex 随机 trace-id（16 bytes，secrets.token_hex）。"""
    return secrets.token_hex(16)


def generate_span_id() -> str:
    """16-char lowercase hex 随机 span-id（8 bytes，secrets.token_hex）。"""
    return secrets.token_hex(8)


# ---- 高级辅助 ----


def build_from_current() -> str:
    """从当前 contextvar 拿 trace_id 拼一个 traceparent（每次新生成 span-id）。

    若当前 contextvar 未设置（None 或空），自动生成新 trace_id 作为兜底，
    与后端 `TraceContext.buildFromMdc()` 行为一致。适用于：定时任务、定时
    调度器、admin 工具等无上游 traceparent 的发起方。
    """
    # 延迟导入避免循环（trace_context 也 import w3c_trace）
    from app.core import trace_context

    trace_id = trace_context.current()[0]
    if not trace_id:
        trace_id = generate_trace_id()
    return build_traceparent(trace_id, generate_span_id())
