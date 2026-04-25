from pydantic_settings import BaseSettings
from typing import Optional

class Settings(BaseSettings):
    NODE_DEFAULT_TIMEOUT: float = 60.0 # Seconds

    # Backend API URL for calling Java backend services
    ORIN_BACKEND_URL: Optional[str] = "http://localhost:8080"

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
    MQ_WORKER_AUTO_START: bool = True

    # Playground runtime tuning
    PLAYGROUND_SUBTASK_POLL_TIMEOUT_SECONDS: float = 420.0
    PLAYGROUND_SUBTASK_POLL_INTERVAL_SECONDS: float = 1.0
    PLAYGROUND_AGENT_MAX_TOKENS: int = 2400
    PLAYGROUND_AGENT_CHAT_TIMEOUT_SECONDS: float = 90.0
    PLAYGROUND_PLANNER_CHAT_TIMEOUT_SECONDS: float = 600.0
    PLAYGROUND_PLANNER_MAX_TOKENS: int = 800
    PLAYGROUND_MERGE_CHAT_TIMEOUT_SECONDS: float = 90.0
    PLAYGROUND_MERGE_MAX_TOKENS: int = 3200

    class Config:
        env_prefix = "ORIN_"
        env_file = ".env"
        extra = "ignore"

settings = Settings()
