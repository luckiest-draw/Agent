# Skill Agent: ReAct agent powered by LangGraph StateGraph + checkpoint
from typing import AsyncIterator, List, Dict, Optional
from langgraph.prebuilt import create_react_agent
from langgraph.checkpoint.memory import MemorySaver
from langchain_core.messages import HumanMessage, SystemMessage, AIMessage
from models.model_manager import create_llm
from tools.tool_registry import get_tools
import logging
import json

logger = logging.getLogger("skill_agent")

# 共享 MemorySaver：所有会话共用，通过 thread_id 隔离
_checkpointer = MemorySaver()


def _build_messages(query: str, history: List[Dict], system_prompt: str = None) -> list:
    messages = []
    if system_prompt:
        messages.append(SystemMessage(content=system_prompt))
    for msg in history:
        role = msg.get("role", "")
        content = msg.get("content", "")
        if role == "user":
            messages.append(HumanMessage(content=content))
        elif role == "assistant":
            messages.append(AIMessage(content=content))
    messages.append(HumanMessage(content=query))
    return messages


async def skill_chat_stream(
    query: str,
    history: List[Dict],
    tools: List[str],
    system_prompt: Optional[str] = None,
    model: str = None,
    temperature: float = 0.7,
    conversation_id: str = "default",
    interrupt_tools: bool = False,
) -> AsyncIterator[str]:
    """基于 LangGraph StateGraph 的 ReAct Agent 流式对话

    - create_react_agent: 自动构建 agent→tools→agent 图循环
    - MemorySaver: 每次 LLM 调用后自动 checkpoint，支持断点恢复
    - interrupt_before: 可选的 Human-in-the-loop，执行工具前暂停
    """
    model_name = model or "deepseek-chat"
    llm = create_llm(model_name=model_name, temperature=temperature, streaming=True)

    tool_instances = get_tools(tools)

    if not tool_instances:
        from agents.chat_agent import chat_stream
        async for content in chat_stream(query, history, model, system_prompt, temperature):
            yield f"data: {json.dumps({'content': content, 'done': False})}\n\n"
        yield f"data: {json.dumps({'content': '', 'done': True})}\n\n"
        return

    default_system = (
        "你是一个智能助手，可以使用工具来获取信息。"
        "当需要查询实时数据、百科知识或学术论文时，主动使用工具。"
    )
    sp = system_prompt or default_system

    agent = create_react_agent(
        model=llm,
        tools=tool_instances,
        checkpointer=_checkpointer,
        interrupt_before=["tools"] if interrupt_tools else None,
    )

    messages = _build_messages(query, history, sp)
    config = {"configurable": {"thread_id": str(conversation_id)}}

    logger.info("LangGraph Agent: query=%s tools=%s thread=%s", query[:50], tools, conversation_id)

    accumulated_output = ""
    try:
        async for event in agent.astream_events(
            {"messages": messages},
            config=config,
            version="v2",
        ):
            kind = event.get("event", "")

            if kind == "on_tool_start":
                tool_name = event.get("name", "unknown")
                tool_input = event.get("data", {}).get("input", "")
                msg = json.dumps({"event": "tool_call", "tool": tool_name, "input": str(tool_input)})
                yield f"data: {msg}\n\n"

            elif kind == "on_tool_end":
                tool_name = event.get("name", "unknown")
                output = event.get("data", {}).get("output", "")
                msg = json.dumps({"event": "tool_result", "tool": tool_name, "output": str(output)[:500]})
                yield f"data: {msg}\n\n"

            elif kind == "on_chat_model_stream":
                chunk = event.get("data", {}).get("chunk", None)
                if chunk and hasattr(chunk, "content") and chunk.content:
                    content = chunk.content
                    if isinstance(content, str):
                        accumulated_output += content
                        yield f"data: {json.dumps({'content': content, 'done': False})}\n\n"

    except Exception as e:
        logger.error("LangGraph Agent error: %s", e)
        if accumulated_output:
            yield f"data: {json.dumps({'content': '', 'done': True})}\n\n"
        else:
            yield f"data: {json.dumps({'error': str(e), 'done': True})}\n\n"
        return

    yield f"data: {json.dumps({'content': '', 'done': True})}\n\n"
    logger.info("LangGraph Agent completed, output %d chars", len(accumulated_output))
