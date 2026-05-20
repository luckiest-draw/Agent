# Agent Engine Configuration
from pydantic_settings import BaseSettings
from typing import Optional

class Settings(BaseSettings):
    # Server
    host: str = "0.0.0.0"
    port: int = 8000

    # Database (Pgvector)
    pg_host: str = "localhost"
    pg_port: int = 5432
    pg_user: str = "agent"
    pg_password: str = "agent123"
    pg_database: str = "agent_platform"

    # Redis / Celery
    redis_url: str = "redis://localhost:6379/0"

    # RabbitMQ
    rabbitmq_url: str = "amqp://agent:agent123@localhost:5672/"

    # Model API Keys (set via environment)
    deepseek_api_key: Optional[str] = None
    deepseek_base_url: str = "https://api.deepseek.com/v1"
    openai_api_key: Optional[str] = None
    openai_base_url: str = "https://api.openai.com/v1"
    qwen_api_key: Optional[str] = None
    qwen_base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    glm_api_key: Optional[str] = None
    glm_base_url: str = "https://open.bigmodel.cn/api/paas/v4"
    gemini_api_key: Optional[str] = None
    gemini_base_url: str = "https://generativelanguage.googleapis.com/v1beta/openai"

    # Default model
    default_model: str = "deepseek-chat"
    embedding_model: str = "text-embedding-3-small"

    # RAG
    chunk_size: int = 500
    chunk_overlap: int = 50
    top_k: int = 5

    # Vision models (support image input)
    vision_models: list = [
        "gpt-4o", "gpt-4-turbo",
        "glm-4v",
        "qwen-vl-plus", "qwen-vl-max",
        "gemini-2.5-flash", "gemini-2.5-pro",
    ]

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


settings = Settings()
