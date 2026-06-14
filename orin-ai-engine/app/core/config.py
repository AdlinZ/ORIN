from pydantic_settings import BaseSettings
from typing import Optional

class Settings(BaseSettings):
    NODE_DEFAULT_TIMEOUT: float = 60.0 # Seconds

    # Backend API URL for calling Java backend services
    # Reads ORIN_BACKEND_URL from the environment via env_prefix below.
    BACKEND_URL: Optional[str] = "http://localhost:8080"
    # Optional machine credential used by MQ workers for protected backend APIs.
    # Reads ORIN_BACKEND_AUTHORIZATION from the environment.
    BACKEND_AUTHORIZATION: Optional[str] = None

    # Redis URL for shared memory storage.
    # Default to local Redis so collaboration runtime can read branch_result
    # written by backend without extra env setup.
    REDIS_URL: Optional[str] = "redis://127.0.0.1:6379/0"

    # LLM Settings (used by workflow engine nodes; Playground routes through ORIN backend gateway)
    OPENAI_API_KEY: Optional[str] = None
    OPENAI_BASE_URL: Optional[str] = "https://api.openai.com/v1"

    # RabbitMQ Configuration for Collaboration Module
    RABBITMQ_URL: Optional[str] = "amqp://guest:guest@localhost:5672/"
    COLLAB_QUEUE_NAME: str = "collaboration-task-queue"
    COLLAB_RESULT_QUEUE_NAME: str = "collaboration-task-result-queue"
    COLLAB_EXCHANGE_NAME: str = "collaboration-task-exchange"
    COLLAB_ROUTING_KEY: str = "collaboration.task"
    COLLAB_DLX_NAME: str = "collaboration-task-dlx"
    COLLAB_QUEUE_TTL: int = 300000
    COLLAB_REPLY_EXCHANGE_NAME: str = "collaboration-reply-exchange"
    COLLAB_REPLY_ROUTING_KEY: str = "collaboration.task.result"
    MQ_WORKER_AUTO_START: bool = False
    MQ_WORKER_DISABLED: bool = False
    MQ_CONNECTION_TIMEOUT_SECONDS: float = 5.0
    MQ_WORKER_LOG_THROTTLE_SECONDS: float = 60.0

    # Outbound W3C traceparent injection. False (default) = 每个
    # `app.core.trace_httpx.httpx_client()` 构造的 AsyncClient 都预装
    # 注入 contextvar trace_id 的 request hook。True 跳过注入
    # （local dev / 旧测试）。环境变量：ORIN_OUTBOUND_TRACEPARENT_DISABLED=1
    OUTBOUND_TRACEPARENT_DISABLED: bool = False

    # 结构化 JSON 日志开关。False（默认）= 文本 formatter，与历史 dev 输出
    # 兼容；True = 走 `app.core.logging_formatter.JsonFormatter` 单行 JSON，
    # 字段含 traceId/spanId（来自 TraceContextFilter），对齐后端
    # logstash-logback-encoder 的字段名，便于 ELK / Loki 统一查询。
    # 环境变量：ORIN_LOG_JSON_FORMAT=1
    LOG_JSON_FORMAT: bool = False
    # root logger 级别，默认 INFO。环境变量：ORIN_LOG_LEVEL=DEBUG
    LOG_LEVEL: str = "INFO"

    # Playground runtime tuning
    PLAYGROUND_SUBTASK_POLL_TIMEOUT_SECONDS: float = 420.0
    PLAYGROUND_SUBTASK_POLL_INTERVAL_SECONDS: float = 1.0
    PLAYGROUND_AGENT_MAX_TOKENS: int = 2400
    PLAYGROUND_AGENT_CHAT_TIMEOUT_SECONDS: float = 90.0
    PLAYGROUND_PLANNER_CHAT_TIMEOUT_SECONDS: float = 600.0
    PLAYGROUND_PLANNER_MAX_TOKENS: int = 800
    PLAYGROUND_MERGE_CHAT_TIMEOUT_SECONDS: float = 90.0
    PLAYGROUND_MERGE_MAX_TOKENS: int = 6000

    class Config:
        env_prefix = "ORIN_"
        env_file = ".env"
        extra = "ignore"

    @property
    def ORIN_BACKEND_URL(self) -> Optional[str]:
        """Backward-compatible access for existing runtime code."""
        return self.BACKEND_URL

    @ORIN_BACKEND_URL.setter
    def ORIN_BACKEND_URL(self, value: Optional[str]) -> None:
        self.BACKEND_URL = value

settings = Settings()
