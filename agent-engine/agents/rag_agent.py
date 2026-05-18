# RAG Agent: 文档问答
from agents.chat_agent import rag_chat_stream


async def ask_document(
    query: str,
    document_ids: list = None,
    model: str = None,
    temperature: float = 0.7,
):
    """基于文档的问答"""
    async for chunk in rag_chat_stream(
        query=query,
        history=[],
        model=model,
        temperature=temperature,
    ):
        yield chunk
