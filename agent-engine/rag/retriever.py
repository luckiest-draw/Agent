# RAG Retriever: 检索增强生成
from rag.vector_store import similarity_search
from models.model_manager import create_llm, supports_vision, get_model_string

def retrieve_context(query: str, top_k: int = 5) -> list:
    """检索相关文档片段"""
    docs = similarity_search(query, top_k)
    return [
        {"content": doc.page_content, "metadata": doc.metadata}
        for doc in docs
    ]


def build_rag_prompt(query: str, context_docs: list, system_prompt: str = None) -> str:
    """构建RAG提示词"""
    context = "\n\n---\n\n".join([d["content"] for d in context_docs])
    base = system_prompt or "你是一个智能助手,根据以下参考文档回答用户问题。如果文档中没有相关信息,请如实说明。"
    return f"""{base}

## 参考文档:
{context}

## 用户问题:
{query}

## 回答:"""
