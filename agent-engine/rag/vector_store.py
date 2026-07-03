# Vector Store: Pgvector-based knowledge base
import uuid
import os
from typing import List, Optional, Dict
from langchain_community.vectorstores import PGVector
from config import settings

CONNECTION_STRING = (
    f"postgresql+psycopg2://{settings.pg_user}:{settings.pg_password}"
    f"@{settings.pg_host}:{settings.pg_port}/{settings.pg_database}"
)

# BGE 中文模型缓存目录（首次运行自动下载 ~1.3GB）
BGE_MODEL_NAME = "BAAI/bge-large-zh-v1.5"
BGE_CACHE_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), "models", "bge-cache")

_bge_available = None


def _check_bge() -> bool:
    """检测 BGE 模型是否可用（FlagEmbedding 已安装且模型已缓存）"""
    global _bge_available
    if _bge_available is not None:
        return _bge_available
    try:
        from transformers import AutoModel
        from sentence_transformers import SentenceTransformer
        _bge_available = True
    except ImportError:
        _bge_available = False
        print("⚠ BGE not installed, falling back to OpenAI embedding.")
        print("  Install: pip install FlagEmbedding sentence-transformers")
    except Exception:
        _bge_available = False
    return _bge_available


def create_embeddings(model: str = None):
    """创建 embedding 模型。

    优先级：
    1. BGA BGE-large-zh-v1.5（免费、本地、中文最优）
    2. OpenAI text-embedding-3-small（需要 API key）
    """
    model_name = model or settings.embedding_model

    # 优先使用 BGE 中文 embedding
    if _check_bge():
        from langchain_community.embeddings import HuggingFaceEmbeddings
        import logging
        logger = logging.getLogger("vector_store")

        # BGE 模型要求查询时加 instruction prefix
        os.makedirs(BGE_CACHE_DIR, exist_ok=True)
        return HuggingFaceEmbeddings(
            model_name=BGE_MODEL_NAME,
            cache_folder=BGE_CACHE_DIR,
            model_kwargs={"device": "cpu"},
            encode_kwargs={
                "normalize_embeddings": True,  # BGE 要求归一化
            },
        )

    # 降级：OpenAI embedding
    from langchain_openai import OpenAIEmbeddings
    return OpenAIEmbeddings(
        model=model_name,
        openai_api_key=settings.openai_api_key or "not-needed",
        openai_api_base=settings.openai_base_url,
    )


def get_vector_store(collection_name: str = "agent_knowledge"):
    return PGVector(
        connection_string=CONNECTION_STRING,
        embedding_function=create_embeddings(),
        collection_name=collection_name,
    )


def add_documents(docs: List[tuple[str, dict]], collection_name: str = "agent_knowledge"):
    """添加文本块到向量库, docs: [(text, metadata), ...]"""
    from langchain_core.documents import Document
    store = get_vector_store(collection_name)
    documents = []
    for text, metadata in docs:
        doc_id = str(uuid.uuid7())
        documents.append(Document(page_content=text, metadata=metadata, id=doc_id))
    store.add_documents(documents)
    return len(documents)


def similarity_search(query: str, top_k: int = None, collection_name: str = "agent_knowledge"):
    """相似度检索。使用 BGE 时自动加 instruction prefix 提升召回质量。"""
    store = get_vector_store(collection_name)
    k = top_k or settings.top_k
    # BGE 查询 instruction：为检索任务加语义引导
    if _check_bge() and query:
        query = f"为这个句子生成表示以用于检索相关文章：{query}"
    return store.similarity_search(query, k=k)
