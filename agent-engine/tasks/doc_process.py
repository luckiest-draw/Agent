# Document Processing Task: 文档向量化异步任务
import os
import tempfile
import requests
from tasks.celery_app import celery_app
from config import settings
from rag.vector_store import add_documents


@celery_app.task(bind=True, name="doc.process")
def process_document(self, chunk_id: int, content: str, chunk_type: str,
                     image_path: str = None, metadata: dict = None):
    """处理文档分片: 文本向量化或图片多模态处理"""
    try:
        docs = []
        meta = metadata or {}

        if chunk_type == "text" and content:
            # 文本直接向量化
            docs.append((content, meta))

        elif chunk_type == "image" and image_path:
            # 图片需要多模态模型生成描述
            if os.path.exists(image_path):
                description = describe_image(image_path)
                docs.append((description, {**meta, "imagePath": image_path}))
            else:
                # 图片路径不在本机,可能需从Java后端获取
                docs.append((f"[图片: {image_path}]", meta))

        if docs:
            count = add_documents(docs)
            return {"status": "done", "chunkId": chunk_id, "vectorizedCount": count}
        return {"status": "skipped", "chunkId": chunk_id, "reason": "empty content"}

    except Exception as e:
        return {"status": "failed", "chunkId": chunk_id, "error": str(e)}


def describe_image(image_path: str) -> str:
    """使用多模态模型描述图片(需vision-capable模型)"""
    import base64
    from openai import OpenAI

    client = OpenAI(
        api_key=settings.openai_api_key or "not-needed",
        base_url=settings.openai_base_url,
    )

    with open(image_path, "rb") as f:
        b64_image = base64.b64encode(f.read()).decode()

    try:
        response = client.chat.completions.create(
            model="gpt-4o",
            messages=[{
                "role": "user",
                "content": [
                    {"type": "text", "text": "请详细描述这张图片的内容,包括文字、图表、布局等所有细节。"},
                    {"type": "image_url", "image_url": {"url": f"data:image/png;base64,{b64_image}"}},
                ],
            }],
            max_tokens=500,
        )
        return response.choices[0].message.content
    except Exception as e:
        return f"[图片描述失败: {e}]"
