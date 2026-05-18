# Chat Agent: 多轮对话 + 流式输出
from typing import AsyncIterator, List, Dict
from langchain_core.messages import HumanMessage, SystemMessage, AIMessage
from models.model_manager import create_llm
from rag.retriever import retrieve_context, build_rag_prompt


def format_messages(history: List[Dict], system_prompt: str = None) -> list:
    """将历史消息转为LangChain格式"""
    messages = []
    if system_prompt:
        messages.append(SystemMessage(content=system_prompt))
    for msg in history:
        role = msg.get("role")
        content = msg.get("content", "")
        if role == "user":
            messages.append(HumanMessage(content=content))
        elif role == "assistant":
            messages.append(AIMessage(content=content))
    return messages


async def chat_stream(
    query: str,
    history: List[Dict],
    model: str = None,
    system_prompt: str = None,
    temperature: float = 0.7,
) -> AsyncIterator[str]:
    """流式对话,返回SSE事件"""
    llm = create_llm(model_name=model, temperature=temperature, streaming=True)
    messages = format_messages(history, system_prompt)
    messages.append(HumanMessage(content=query))

    async for chunk in llm.astream(messages):
        content = chunk.content if hasattr(chunk, "content") else str(chunk)
        if content:
            yield content


async def rag_chat_stream(
    query: str,
    history: List[Dict],
    model: str = None,
    system_prompt: str = None,
    temperature: float = 0.7,
    top_k: int = 5,
) -> AsyncIterator[str]:
    """RAG增强对话流式输出"""
    # 检索相关文档
    context_docs = retrieve_context(query, top_k)
    # 构建RAG提示词
    rag_system = build_rag_prompt(query, context_docs, system_prompt)

    llm = create_llm(model_name=model, temperature=temperature, streaming=True)
    messages = format_messages(history)
    messages.insert(0, SystemMessage(content=rag_system))
    messages.append(HumanMessage(content=query))

    async for chunk in llm.astream(messages):
        content = chunk.content if hasattr(chunk, "content") else str(chunk)
        if content:
            yield content
