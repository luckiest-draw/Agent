# Skill Agent: ReAct + Review Loop powered by LangGraph StateGraph
from typing import AsyncIterator, List, Dict, Optional, Annotated, TypedDict
from langgraph.prebuilt import ToolNode, tools_condition
from langgraph.graph import StateGraph, START, END
from langgraph.checkpoint.memory import MemorySaver
from langchain_core.messages import HumanMessage, SystemMessage, AIMessage, BaseMessage
from models.model_manager import create_llm
from tools.tool_registry import get_tools
from langgraph.graph.message import add_messages
import logging
import json

logger = logging.getLogger("skill_agent")
_checkpointer = MemorySaver()


class AgentState(TypedDict):
    messages: Annotated[list, add_messages]
    review_count: int


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
    """LangGraph StateGraph: agent ⇄ tools ⇄ review

    三个节点：
    - agent: LLM 决策（调工具 or 生成回复）
    - tools: 执行工具调用
    - review: 审查 agent 的最终回复，不合格打回重写（最多 2 次）
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
        "回复时要引用工具返回的信息来源。"
    )
    sp = system_prompt or default_system
    llm_with_tools = llm.bind_tools(tool_instances)

    # 审查用 LLM（非流式，只判断 PASS/FAIL）
    review_llm = create_llm(model_name=model_name, temperature=0.0, max_tokens=256, streaming=False)

    def agent_node(state: AgentState):
        response = llm_with_tools.invoke(state["messages"])
        return {"messages": [response]}

    def review_node(state: AgentState):
        count = state.get("review_count", 0)
        if count >= 2:
            logger.info("Review: max retries reached, accepting output")
            return {"review_count": count}

        messages = state["messages"]
        last_ai = [m for m in messages if isinstance(m, AIMessage)
                   and hasattr(m, "content") and m.content
                   and not (hasattr(m, "tool_calls") and m.tool_calls)]
        if not last_ai:
            return {"review_count": count}

        draft = last_ai[-1].content

        # 提取工具调用结果作为审查依据
        tool_msgs = [m for m in messages if hasattr(m, "type") and m.type == "tool"]
        tool_context = ""
        if tool_msgs:
            tool_context = "\n工具查询结果：\n" + "\n".join(
                f"- {m.content[:300]}" for m in tool_msgs[-5:]
            )

        review_prompt = f"""你是一个质量审查员。请审查以下 AI 回复是否存在问题：{tool_context}

AI 回复：
{draft[:1500]}

检查项：
1. 是否基于工具返回的信息作答（不是凭空编造）
2. 是否有事实错误或逻辑矛盾
3. 是否直接回答了用户问题（不是答非所问）

仅回复 PASS 或 FAIL: <问题简述>"""

        review_resp = review_llm.invoke([HumanMessage(content=review_prompt)])
        verdict = review_resp.content.strip()
        logger.info("Review #%d: %s", count + 1, verdict[:120])

        if verdict.upper().startswith("PASS"):
            return {"review_count": count + 1}
        else:
            feedback = SystemMessage(
                content=f"[审查未通过] {verdict}\n请根据以上反馈重新生成回复，确保基于工具返回的信息作答。"
            )
            return {"messages": [feedback], "review_count": count + 1}

    def after_review(state: AgentState):
        """判断审查后走哪条路"""
        messages = state.get("messages", [])
        if not messages:
            return END
        last = messages[-1]
        if isinstance(last, SystemMessage) and "[审查未通过]" in (last.content or ""):
            return "agent"
        return END

    # 构建图
    builder = StateGraph(AgentState)
    builder.add_node("agent", agent_node)
    builder.add_node("tools", ToolNode(tool_instances))
    builder.add_node("review", review_node)

    builder.add_edge(START, "agent")
    builder.add_conditional_edges("agent", tools_condition, {"tools": "tools", END: "review"})
    builder.add_edge("tools", "agent")
    builder.add_conditional_edges("review", after_review, {"agent": "agent", END: END})

    graph = builder.compile(checkpointer=_checkpointer)

    initial_messages = _build_messages(query, history, sp)
    config = {"configurable": {"thread_id": str(conversation_id)}}

    logger.info("Agent+Review: query=%s tools=%s thread=%s", query[:50], tools, conversation_id)

    accumulated_output = ""
    try:
        async for event in graph.astream_events(
            {"messages": initial_messages, "review_count": 0},
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
        logger.error("Agent error: %s", e)
        if accumulated_output:
            yield f"data: {json.dumps({'content': '', 'done': True})}\n\n"
        else:
            yield f"data: {json.dumps({'error': str(e), 'done': True})}\n\n"
        return

    yield f"data: {json.dumps({'content': '', 'done': True})}\n\n"
    logger.info("Agent+Review completed, output %d chars", len(accumulated_output))
