"""
共享内存存储 - 为多智能体协作提供跨分支的共享数据访问
支持 Redis（生产环境）和内存存储（测试环境）
"""
import json
import time
from typing import Any, Dict, Optional

from app.core.config import settings


class SharedMemoryStore:
    """
    共享内存存储 - 支持 Redis 和内存两种后端
    """

    def __init__(self):
        self._redis_client = None
        self._memory_store: Dict[str, Dict[str, Any]] = {}  # Fallback 内存存储
        self._use_redis = bool(settings.REDIS_URL)

    def _get_redis(self):
        """获取 Redis 客户端（延迟初始化）"""
        if self._redis_client is None and self._use_redis:
            try:
                import redis
                self._redis_client = redis.from_url(settings.REDIS_URL, decode_responses=True)
                # 测试连接
                self._redis_client.ping()
            except Exception:
                self._use_redis = False
                self._redis_client = None
        return self._redis_client

    def _make_key(self, key: str, namespace: str = "orin:memory") -> str:
        """生成带命名空间前缀的 key"""
        return f"{namespace}:{key}"

    def get(self, key: str, default: Any = None, namespace: str = "orin:memory") -> Any:
        """
        获取共享内存中的值

        Args:
            key: 内存键
            default: 默认值（当键不存在时返回）
            namespace: 命名空间，用于隔离不同工作流的内存

        Returns:
            存储的值，如果不存在则返回 default
        """
        redis = self._get_redis()
        full_key = self._make_key(key, namespace)

        if redis:
            try:
                value = redis.get(full_key)
                if value is None:
                    return default
                return json.loads(value)
            except Exception:
                return default
        else:
            # 使用内存存储
            entry = self._memory_store.get(full_key)
            if entry is None:
                return default
            # 检查是否过期
            if entry.get("expires_at") and entry["expires_at"] < time.time():
                del self._memory_store[full_key]
                return default
            return entry.get("value", default)

    def set(
        self,
        key: str,
        value: Any,
        ttl: Optional[float] = None,
        namespace: str = "orin:memory",
        merge: bool = False
    ) -> bool:
        """
        设置共享内存的值

        Args:
            key: 内存键
            value: 要存储的值
            ttl: 过期时间（秒），None 表示永不过期
            namespace: 命名空间
            merge: 是否合并（如果现有值是 dict）

        Returns:
            是否成功存储
        """
        redis = self._get_redis()
        full_key = self._make_key(key, namespace)

        if redis:
            try:
                if merge:
                    existing = redis.get(full_key)
                    if existing:
                        existing_data = json.loads(existing)
                        if isinstance(existing_data, dict) and isinstance(value, dict):
                            existing_data.update(value)
                            value = existing_data

                serialized = json.dumps(value, ensure_ascii=False)

                if ttl:
                    redis.setex(full_key, ttl, serialized)
                else:
                    redis.set(full_key, serialized)
                return True
            except Exception:
                return False
        else:
            # 使用内存存储
            entry = {
                "value": value,
                "created_at": time.time(),
                "expires_at": time.time() + ttl if ttl else None
            }
            self._memory_store[full_key] = entry
            return True

    def delete(self, key: str, namespace: str = "orin:memory") -> bool:
        """删除共享内存中的值"""
        redis = self._get_redis()
        full_key = self._make_key(key, namespace)

        if redis:
            try:
                redis.delete(full_key)
                return True
            except Exception:
                return False
        else:
            if full_key in self._memory_store:
                del self._memory_store[full_key]
                return True
            return False

    def exists(self, key: str, namespace: str = "orin:memory") -> bool:
        """检查键是否存在"""
        redis = self._get_redis()
        full_key = self._make_key(key, namespace)

        if redis:
            try:
                return redis.exists(full_key) > 0
            except Exception:
                return False
        else:
            entry = self._memory_store.get(full_key)
            if entry is None:
                return False
            if entry.get("expires_at") and entry["expires_at"] < time.time():
                del self._memory_store[full_key]
                return False
            return True

    def keys(self, pattern: str = "*", namespace: str = "orin:memory") -> list:
        """获取所有匹配模式的键"""
        redis = self._get_redis()
        full_pattern = self._make_key(pattern, namespace)

        if redis:
            try:
                return [k.replace(f"{namespace}:", "") for k in redis.keys(full_pattern)]
            except Exception:
                return []
        else:
            prefix = f"{namespace}:"
            return [
                k.replace(prefix, "")
                for k in self._memory_store.keys()
                if k.startswith(prefix)
            ]

    def clear_namespace(self, namespace: str = "orin:memory") -> int:
        """清空指定命名空间的所有键"""
        redis = self._get_redis()
        full_pattern = self._make_key("*", namespace)

        if redis:
            try:
                keys = redis.keys(full_pattern)
                if keys:
                    return redis.delete(*keys)
                return 0
            except Exception:
                return 0
        else:
            prefix = f"{namespace}:"
            to_delete = [k for k in self._memory_store.keys() if k.startswith(prefix)]
            for k in to_delete:
                del self._memory_store[k]
            return len(to_delete)


# 全局共享内存实例
shared_memory = SharedMemoryStore()
