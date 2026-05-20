# Chat Agent: 多轮对话 + 流式输出 + 多模态图片支持
from typing import AsyncIterator, List, Dict, Optional
from langchain_core.messages import HumanMessage, SystemMessage, AIMessage
from models.model_manager import create_llm, supports_vision
from models.multimodal import build_multimodal_content
from rag.retriever import retrieve_context, build_rag_prompt
import logging

logger = logging.getLogger("chat_agent")


def format_messages(history: List[Dict], system_prompt: str = None) -> list:
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
    image_url: Optional[str] = None,
) -> AsyncIterator[str]:
    model_name = model or "deepseek-chat"

    if image_url and not supports_vision(model_name):
        yield f"[错误] 模型 {model_name} 不支持图片输入，请切换到 gpt-4o / glm-4v / qwen-vl / gemini"
        return

    llm = create_llm(model_name=model_name, temperature=temperature, streaming=True)
    messages = format_messages(history, system_prompt)

    if image_url:
        msg_content = build_multimodal_content(query, image_url)
        messages.append(HumanMessage(content=msg_content))
    else:
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
    image_url: Optional[str] = None,
) -> AsyncIterator[str]:
    model_name = model or "deepseek-chat"

    if image_url and not supports_vision(model_name):
        yield f"[错误] 模型 {model_name} 不支持图片输入，请切换到 gpt-4o / glm-4v / qwen-vl / gemini"
        return

    context_docs = retrieve_context(query, top_k)
    rag_system = build_rag_prompt(query, context_docs, system_prompt)

    llm = create_llm(model_name=model_name, temperature=temperature, streaming=True)
    messages = format_messages(history)
    messages.insert(0, SystemMessage(content=rag_system))

    if image_url:
        msg_content = build_multimodal_content(query, image_url)
        messages.append(HumanMessage(content=msg_content))
    else:
        messages.append(HumanMessage(content=query))

    async for chunk in llm.astream(messages):
        content = chunk.content if hasattr(chunk, "content") else str(chunk)
        if content:
            yield content
