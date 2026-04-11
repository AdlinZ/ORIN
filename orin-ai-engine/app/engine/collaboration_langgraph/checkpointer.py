"""
Redis Checkpointer - LangGraph 状态持久化
"""
import logging
import json
from typing import Optional, Any, Dict
from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.checkpoint.base import Checkpoint, CheckpointTuple

logger = logging.getLogger(__name__)


class RedisCheckpointer(BaseCheckpointSaver):
    """Redis 检查点存储"""
    
    def __init__(self, redis_client, namespace: str = "langgraph"):
        self.redis = redis_client
        self.namespace = namespace
    
    def _make_key(self, thread_id: str, checkpoint_id: str) -> str:
        return f"{self.namespace}:{thread_id}:{checkpoint_id}"
    
    def _make_thread_key(self, thread_id: str) -> str:
        return f"{self.namespace}:{thread_id}"
    
    async def put(
        self,
        config: Dict[str, Any],
        checkpoint: Checkpoint,
        metadata: Dict[str, Any]
    ) -> None:
        """保存检查点"""
        thread_id = config.get("configurable", {}).get("thread_id", "")
        checkpoint_id = checkpoint.get("id", "")
        
        if not thread_id or not checkpoint_id:
            return
        
        key = self._make_key(thread_id, checkpoint_id)
        
        # 序列化
        data = {
            "checkpoint": checkpoint,
            "metadata": metadata
        }
        
        await self.redis.set(key, json.dumps(data), ex=7*24*3600)  # 7天过期
        
        # 更新线程索引
        thread_key = self._make_thread_key(thread_id)
        await self.redis.sadd(thread_key, checkpoint_id)
        
        logger.debug(f"[RedisCheckpointer] 保存检查点: {thread_id}/{checkpoint_id}")
    
    async def get(self, config: Dict[str, Any]) -> Optional[CheckpointTuple]:
        """获取最新检查点"""
        thread_id = config.get("configurable", {}).get("thread_id", "")
        
        if not thread_id:
            return None
        
        thread_key = self._make_thread_key(thread_id)
        
        # 获取最新检查点 ID
        latest = await self.redis.smembers(thread_key)
        if not latest:
            return None
        
        # 按时间排序，取最新的
        checkpoint_ids = sorted([cid.decode() if isinstance(cid, bytes) else cid for cid in latest])
        latest_id = checkpoint_ids[-1] if checkpoint_ids else None
        
        if not latest_id:
            return None
        
        key = self._make_key(thread_id, latest_id)
        data = await self.redis.get(key)
        
        if not data:
            return None
        
        data = json.loads(data)
        
        return CheckpointTuple(
            config={"configurable": {"thread_id": thread_id, "checkpoint_id": latest_id}},
            checkpoint=data["checkpoint"],
            metadata=data.get("metadata", {}),
            parent_config=config
        )
    
    async def list_versions(
        self,
        thread_id: str,
        filter: Dict[str, Any] = None
    ) -> list:
        """列出所有版本"""
        thread_key = self._make_thread_key(thread_id)
        
        checkpoint_ids = await self.redis.smembers(thread_key)
        
        versions = []
        for cid in checkpoint_ids:
            cid_str = cid.decode() if isinstance(cid, bytes) else cid
            versions.append({"version": cid_str})
        
        return versions
    
    async def get_version(
        self,
        thread_id: str,
        version: str
    ) -> Optional[Checkpoint]:
        """获取指定版本检查点"""
        key = self._make_key(thread_id, version)
        
        data = await self.redis.get(key)
        if not data:
            return None
        
        return json.loads(data).get("checkpoint")
    
    async def delete_version(self, thread_id: str, version: str) -> None:
        """删除版本"""
        key = self._make_key(thread_id, version)
        thread_key = self._make_thread_key(thread_id)
        
        await self.redis.delete(key)
        await self.redis.srem(thread_key, version)
        
        logger.info(f"[RedisCheckpointer] 删除检查点: {thread_id}/{version}")


# 全局实例
_redis_checkpointer = None


def get_redis_checkpointer(redis_client) -> RedisCheckpointer:
    """获取 RedisCheckpointer 实例"""
    global _redis_checkpointer
    
    if _redis_checkpointer is None:
        _redis_checkpointer = RedisCheckpointer(redis_client)
    
    return _redis_checkpointer
