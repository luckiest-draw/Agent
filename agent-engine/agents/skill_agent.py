# Skill Agent: tool-calling agent with streaming support
from typing import AsyncIterator, List, Dict, Optional
from langchain.agents import AgentExecutor, create_tool_calling_agent
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_core.messages import HumanMessage, SystemMessage, AIMessage
from models.model_manager import create_llm
from tools.tool_registry import get_tools
import logging
import json

logger = logging.getLogger("skill_agent")


def _build_history_messages(history: List[Dict]) -> list:
    """将 Java 传过来的 history dict 转为 LangChain messages"""
    messages = []
    for msg in history:
        role = msg.get("role", "")
        content = msg.get("content", "")
        if role == "user":
            messages.append(HumanMessage(content=content))
        elif role == "assistant":
            messages.append(AIMessage(content=content))
    return messages


async def skill_chat_stream(
    query: str,
    history: List[Dict],
    tools: List[str],
    system_prompt: Optional[str] = None,
    model: str = None,
    temperature: float = 0.7,
) -> AsyncIterator[str]:
    """使用工具调用 Agent 的流式对话，输出 SSE 格式的 JSON 事件"""
    model_name = model or "deepseek-chat"
    llm = create_llm(model_name=model_name, temperature=temperature, streaming=True)

    tool_instances = get_tools(tools)
    has_tools = len(tool_instances) > 0

    if not has_tools:
        # 没有工具，降级为普通对话，但保持相同的事件格式
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

    prompt = ChatPromptTemplate.from_messages([
        ("system", sp),
        MessagesPlaceholder(variable_name="chat_history", optional=True),
        ("user", "{input}"),
        ("placeholder", "{agent_scratchpad}"),
    ])

    agent = create_tool_calling_agent(llm, tool_instances, prompt)
    executor = AgentExecutor(
        agent=agent,
        tools=tool_instances,
        verbose=True,
        max_iterations=5,
        handle_parsing_errors=True,
    )

    chat_history = _build_history_messages(history)

    logger.info("SkillAgent: query=%s tools=%s", query[:50], tools)

    accumulated_output = ""
    try:
        async for event in executor.astream_events(
            {"input": query, "chat_history": chat_history},
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
        logger.error("SkillAgent error: %s", e)
        if accumulated_output:
            yield f"data: {json.dumps({'content': '', 'done': True})}\n\n"
        else:
            yield f"data: {json.dumps({'error': str(e), 'done': True})}\n\n"
        return

    yield f"data: {json.dumps({'content': '', 'done': True})}\n\n"
    logger.info("SkillAgent completed, output %d chars", len(accumulated_output))
