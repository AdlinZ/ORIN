from pydantic_settings import BaseSettings
from typing import Optional

class Settings(BaseSettings):
    NODE_DEFAULT_TIMEOUT: float = 60.0 # Seconds

    # Backend API URL for calling Java backend services
    ORIN_BACKEND_URL: Optional[str] = "http://localhost:8080"

    # Redis URL for shared memory storage (optional - falls back to context)
    REDIS_URL: Optional[str] = None

    # LLM Settings
    OPENAI_API_KEY: Optional[str] = None
    OPENAI_BASE_URL: Optional[str] = "https://api.openai.com/v1"

    # RabbitMQ Configuration for Collaboration Module
    RABBITMQ_URL: Optional[str] = "amqp://guest:guest@localhost:5672/"
    COLLAB_QUEUE_NAME: str = "collaboration-task-queue"
    COLLAB_RESULT_QUEUE_NAME: str = "collaboration-task-result-queue"
    COLLAB_EXCHANGE_NAME: str = "collaboration-task-exchange"
    COLLAB_REPLY_EXCHANGE_NAME: str = "collaboration-reply-exchange"
    COLLAB_REPLY_ROUTING_KEY: str = "collaboration.task.result"

    class Config:
        env_prefix = "ORIN_"
        env_file = ".env"
        extra = "ignore"

settings = Settings()
