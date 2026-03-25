"""
协作上下文管理器 - 负责上下文窗口管理、摘要压缩、跨 Agent 结果合并
"""
import json
import hashlib
from typing import Dict, Any, List, Optional, Callable
from dataclasses import dataclass, field
from datetime import datetime


@dataclass
class ContextWindow:
    """上下文窗口"""
    max_tokens: int = 128000
    current_tokens: int = 0
    reserved_tokens: int = 10000  # 保留给系统消息

    @property
    def available_tokens(self) -> int:
        return max(0, self.max_tokens - self.reserved_tokens - self.current_tokens)


@dataclass
class ContextEntry:
    """上下文条目"""
    key: str
    value: Any
    tokens: int
    source: str  # agent_id or role
    timestamp: datetime = field(default_factory=datetime.now)
    priority: int = 0  # 越高越重要


class ContextManager:
    """
    上下文管理器 - 控制 token 消耗、支持摘要压缩、实现跨 agent 结果合并
    """

    def __init__(
        self,
        max_tokens: int = 128000,
        reserved_tokens: int = 10000,
        compression_threshold: float = 0.8,
        summarizer: Optional[Callable] = None
    ):
        self.window = ContextWindow(max_tokens=max_tokens, reserved_tokens=reserved_tokens)
        self.compression_threshold = compression_threshold

        # 上下文存储
        self.entries: Dict[str, ContextEntry] = {}

        # 摘要历史
        self.summaries: List[Dict[str, Any]] = []

        # 摘要器（可选，用于生成摘要）
        self.summarizer = summarizer

    def add_entry(
        self,
        key: str,
        value: Any,
        tokens: int,
        source: str,
        priority: int = 0
    ) -> bool:
        """
        添加上下文条目

        Returns:
            是否成功添加（可能触发压缩）
        """
        entry = ContextEntry(
            key=key,
            value=value,
            tokens=tokens,
            source=source,
            priority=priority
        )

        # 检查是否需要压缩
        self.window.current_tokens += tokens

        if self.window.current_tokens > self.window.max_tokens * self.compression_threshold:
            # 触发压缩
            self._compress()

        # 如果仍然超出，尝试移除低优先级条目
        while self.window.current_tokens > self.window.available_tokens:
            if not self._evict_low_priority():
                # 无法再移除，返回失败
                return False

        self.entries[key] = entry
        return True

    def get_entry(self, key: str) -> Optional[Any]:
        """获取上下文条目值"""
        entry = self.entries.get(key)
        return entry.value if entry else None

    def get_all_context(self) -> Dict[str, Any]:
        """获取所有上下文（用于构建 prompt）"""
        return {
            key: entry.value
            for key, entry in self.entries.items()
        }

    def _compress(self):
        """压缩上下文 - 生成摘要"""
        # 按优先级排序
        sorted_entries = sorted(
            self.entries.values(),
            key=lambda e: (e.priority, e.timestamp.timestamp()),
            reverse=True
        )

        # 保留高优先级条目
        kept_entries = []
        kept_tokens = 0

        for entry in sorted_entries:
            if kept_tokens + entry.tokens <= self.window.available_tokens:
                kept_entries.append(entry)
                kept_tokens += entry.tokens

        # 生成摘要
        summary = self._generate_summary(kept_entries)

        # 清空并重新添加
        self.entries.clear()

        # 添加摘要作为特殊条目
        self.entries["__summary"] = ContextEntry(
            key="__summary",
            value=summary,
            tokens=self._estimate_tokens(summary),
            source="system",
            priority=100
        )
        self.window.current_tokens = self._estimate_tokens(summary)

        # 重新添加高优先级条目
        for entry in kept_entries:
            self.entries[entry.key] = entry
            self.window.current_tokens += entry.tokens

    def _generate_summary(self, entries: List[ContextEntry]) -> Dict[str, Any]:
        """生成摘要"""
        summary = {
            "timestamp": datetime.now().isoformat(),
            "entry_count": len(entries),
            "total_tokens": sum(e.tokens for e in entries),
            "sources": list(set(e.source for e in entries)),
            "entries": []
        }

        # 如果有 summarizer，使用它
        if self.summarizer:
            try:
                content = "\n".join([f"{e.key}: {str(e.value)[:200]}" for e in entries])
                summary["summary"] = self.summarizer(content)
            except Exception:
                pass
        else:
            # 简单摘要
            summary["entries"] = [
                {
                    "key": e.key,
                    "source": e.source,
                    "preview": str(e.value)[:100]
                }
                for e in entries[:10]  # 最多 10 个
            ]

        self.summaries.append(summary)
        return summary

    def _evict_low_priority(self) -> bool:
        """移除最低优先级条目"""
        if not self.entries:
            return False

        # 找到最低优先级（非系统保留）
        min_priority = float('inf')
        min_key = None

        for key, entry in self.entries.items():
            if key != "__summary" and entry.priority < min_priority:
                min_priority = entry.priority
                min_key = key

        if min_key:
            removed = self.entries.pop(min_key)
            self.window.current_tokens -= removed.tokens
            return True

        return False

    def merge_agent_results(
        self,
        agent_id: str,
        result: Any,
        metadata: Optional[Dict] = None
    ) -> Dict[str, Any]:
        """
        合并 Agent 结果

        跨 agent 结果合并策略：
        1. 如果是文本结果，追加到共享上下文
        2. 如果是结构化数据，合并到统一输出
        3. 如果有冲突，保留高优先级
        """
        merged = {
            "agent_id": agent_id,
            "result": result,
            "timestamp": datetime.now().isoformat()
        }

        if metadata:
            merged["metadata"] = metadata

        # 存储结果
        result_key = f"agent_result_{agent_id}"
        tokens = self._estimate_tokens(result)

        self.add_entry(
            key=result_key,
            value=merged,
            tokens=tokens,
            source=agent_id,
            priority=50
        )

        return merged

    def get_merged_output(self) -> Dict[str, Any]:
        """获取合并后的输出"""
        output = {
            "summary": self.get_entry("__summary"),
            "agent_results": {},
            "context": self.get_all_context()
        }

        # 收集所有 agent 结果
        for key, entry in self.entries.items():
            if key.startswith("agent_result_"):
                agent_id = key.replace("agent_result_", "")
                output["agent_results"][agent_id] = entry.value

        return output

    def _estimate_tokens(self, content: Any) -> int:
        """估算 token 数量（简单实现：字符数/4）"""
        if isinstance(content, str):
            return len(content) // 4
        elif isinstance(content, dict):
            return len(json.dumps(content)) // 4
        else:
            return len(str(content)) // 4

    def clear(self):
        """清空上下文"""
        self.entries.clear()
        self.window.current_tokens = 0

    def get_stats(self) -> Dict[str, Any]:
        """获取上下文统计"""
        return {
            "max_tokens": self.window.max_tokens,
            "current_tokens": self.window.current_tokens,
            "available_tokens": self.window.available_tokens,
            "entry_count": len(self.entries),
            "summary_count": len(self.summaries)
        }


# 全局上下文管理器实例
context_manager = ContextManager()