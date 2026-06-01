# Chat Agent: 多轮对话 + 流式输出 + 多模态 + 熔断降级
from typing import AsyncIterator, List, Dict, Optional
import asyncio
import logging
from langchain_core.messages import HumanMessage, SystemMessage, AIMessage
from models.model_manager import create_llm, supports_vision
from models.multimodal import build_multimodal_content
from models.circuit_breaker import health_monitor, resolve_fallback, get_degrade_config
from rag.retriever import retrieve_context, build_rag_prompt

logger = logging.getLogger("chat_agent")

FIRST_TOKEN_TIMEOUT = 8.0  # 首包超时秒数


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


async def _stream_with_probe(llm, messages) -> AsyncIterator[str]:
    """本地生成器：用 asyncio.wait_for 探测流式首包"""
    agen = llm.astream(messages)

    try:
        agen_aiter = agen.__aiter__()
        first = await asyncio.wait_for(agen_aiter.__anext__(), timeout=FIRST_TOKEN_TIMEOUT)
        content = first.content if hasattr(first, "content") else str(first)
        if content:
            yield content
        async for chunk in agen_aiter:
            c = chunk.content if hasattr(chunk, "content") else str(chunk)
            if c:
                yield c
    except asyncio.TimeoutError:
        raise TimeoutError(f"First token timeout ({FIRST_TOKEN_TIMEOUT}s)")


async def chat_stream(
    query: str,
    history: List[Dict],
    model: str = None,
    system_prompt: str = None,
    temperature: float = 0.7,
    image_url: Optional[str] = None,
) -> AsyncIterator[str]:
    """流式对话，带三态熔断 + 优先级降级 + 首包探测"""
    model_name = model or "deepseek-chat"
    candidates = resolve_fallback(model_name)

    last_error = None
    for level, candidate in enumerate(candidates):
        if health_monitor.is_model_open(candidate):
            logger.info("Circuit %s OPEN, skipping", candidate)
            continue

        if image_url and not supports_vision(candidate):
            continue

        degrade = get_degrade_config(level)
        effective_temp = temperature if level == 0 else degrade["temperature"]

        try:
            llm = create_llm(model_name=candidate, temperature=effective_temp,
                             max_tokens=degrade["max_tokens"], streaming=True)
            messages = format_messages(history, system_prompt)
            if image_url:
                messages.append(HumanMessage(content=build_multimodal_content(query, image_url)))
            else:
                messages.append(HumanMessage(content=query))

            logger.info("chat_stream: trying %s (level=%d)", candidate, level)
            async for token in _stream_with_probe(llm, messages):
                yield token
            health_monitor.success(candidate)
            return

        except (TimeoutError, Exception) as e:
            health_monitor.failure(candidate)
            last_error = e
            logger.warning("Model %s failed (level=%d): %s", candidate, level, e)
            continue

    err_msg = f"所有模型不可用, last_error={last_error}"
    logger.error(err_msg)
    yield f"[错误] {err_msg}"


async def rag_chat_stream(
    query: str,
    history: List[Dict],
    model: str = None,
    system_prompt: str = None,
    temperature: float = 0.7,
    top_k: int = 5,
    image_url: Optional[str] = None,
) -> AsyncIterator[str]:
    """RAG 增强对话，带熔断降级"""
    model_name = model or "deepseek-chat"
    candidates = resolve_fallback(model_name)

    context_docs = retrieve_context(query, top_k)
    rag_system = build_rag_prompt(query, context_docs, system_prompt)

    last_error = None
    for level, candidate in enumerate(candidates):
        if health_monitor.is_model_open(candidate):
            continue
        if image_url and not supports_vision(candidate):
            continue

        degrade = get_degrade_config(level)
        effective_temp = temperature if level == 0 else degrade["temperature"]

        try:
            llm = create_llm(model_name=candidate, temperature=effective_temp,
                             max_tokens=degrade["max_tokens"], streaming=True)
            messages = format_messages(history)
            messages.insert(0, SystemMessage(content=rag_system))
            if image_url:
                messages.append(HumanMessage(content=build_multimodal_content(query, image_url)))
            else:
                messages.append(HumanMessage(content=query))

            logger.info("rag_chat_stream: trying %s (level=%d)", candidate, level)
            async for token in _stream_with_probe(llm, messages):
                yield token
            health_monitor.success(candidate)
            return

        except (TimeoutError, Exception) as e:
            health_monitor.failure(candidate)
            last_error = e
            logger.warning("RAG model %s failed (level=%d): %s", candidate, level, e)
            continue

    err_msg = f"所有模型不可用, last_error={last_error}"
    logger.error(err_msg)
    yield f"[错误] {err_msg}"
