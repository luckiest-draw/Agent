# Celery: 异步任务队列
from celery import Celery
from config import settings

celery_app = Celery(
    "agent_engine",
    broker=settings.rabbitmq_url,
    backend=settings.redis_url,
    include=["tasks.doc_process", "tasks.workflow_exec"],
)

celery_app.conf.update(
    task_serializer="json",
    result_serializer="json",
    accept_content=["json"],
    timezone="Asia/Shanghai",
    enable_utc=True,
    task_track_started=True,
    task_soft_time_limit=600,  # 10分钟
    task_time_limit=900,
)
