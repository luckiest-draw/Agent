# Vector Store: Pgvector-based knowledge base
import uuid
from typing import List, Optional, Dict
from langchain_community.vectorstores import PGVector
from langchain_openai import OpenAIEmbeddings
from config import settings

CONNECTION_STRING = (
    f"postgresql+psycopg2://{settings.pg_user}:{settings.pg_password}"
    f"@{settings.pg_host}:{settings.pg_port}/{settings.pg_database}"
)


def create_embeddings(model: str = None):
    """创建embedding模型"""
    return OpenAIEmbeddings(
        model=model or settings.embedding_model,
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
    store = get_vector_store(collection_name)
    k = top_k or settings.top_k
    return store.similarity_search(query, k=k)
