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
    review_history: str  # 累积审查反馈，便于追踪


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

    四节点：
    - agent: LLM 决策（调工具 → 生成回复）
    - tools: 执行工具调用
    - review: 审查回复质量
      · PASS → END
      · FAIL → 注入反馈 → agent 重试（换工具/换策略）
      · 最多 3 次重试，全部失败 → 告知用户原因 + 建议方案
       （如 Claude Code：网页搜不到就换搜索引擎，都搜不到就建议用户检查网络）
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
        "如果某个工具返回了空结果或报错，尝试用其他工具获取信息。"
        "回复时要引用工具返回的信息来源。"
    )
    sp = system_prompt or default_system
    llm_with_tools = llm.bind_tools(tool_instances)

    # 审查用 LLM（非流式）
    review_llm = create_llm(model_name=model_name, temperature=0.0, max_tokens=512, streaming=False)

    def agent_node(state: AgentState):
        response = llm_with_tools.invoke(state["messages"])
        return {"messages": [response]}

    def review_node(state: AgentState):
        count = state.get("review_count", 0)
        messages = state["messages"]

        # 提取最后一个纯文本 AI 回复
        last_ai = [m for m in messages if isinstance(m, AIMessage)
                   and hasattr(m, "content") and m.content
                   and not (hasattr(m, "tool_calls") and m.tool_calls)]
        if not last_ai:
            return {"review_count": count, "review_history": state.get("review_history", "")}

        draft = last_ai[-1].content
        prev_history = state.get("review_history", "")

        # 提取工具结果作为审查依据
        tool_msgs = [m for m in messages if hasattr(m, "type") and m.type == "tool"]
        tool_context = ""
        if tool_msgs:
            tool_context = "\n工具查询结果：\n" + "\n".join(
                f"- {m.content[:300]}" for m in tool_msgs[-5:]
            )

        # 检查工具是否全部失败
        tool_errors = any(
            "error" in (m.content or "").lower()
            or "failed" in (m.content or "").lower()
            or "no results" in (m.content or "").lower()
            for m in tool_msgs[-3:]
        ) if tool_msgs else False

        # 最后一次审查：接受回复，但要加上失败说明
        if count >= 3:
            logger.info("Review: max retries (%d) reached", count)
            if tool_errors:
                fallback = (
                    "\n\n---\n⚠️ **本次回复可能不够理想**：工具多次查询未能获取完整数据。"
                    "\n建议你：\n"
                    + ("- 尝试更换搜索引擎或网络环境后重试\n" if "搜索" in " ".join(tools) else "")
                    + "- 换个更具体的搜索词重新提问\n"
                    + "- 如果问题持续，可能是外部服务暂时不可用，稍后再试"
                )
                return {
                    "messages": [AIMessage(content=draft + fallback)],
                    "review_count": count + 1,
                    "review_history": prev_history,
                }
            # 普通审查已达上限，直接放行
            return {"review_count": count + 1, "review_history": prev_history}

        review_prompt = f"""你是一个质量审查员。审查以下 AI 回复是否存在问题：{tool_context}

AI 回复：
{draft[:1500]}

检查项：
1. 是否基于工具返回的信息作答（不是凭空编造）
2. 是否有事实错误或逻辑矛盾
3. 是否遗漏了重要的工具查询结果
4. 如果工具返回为空或报错，是否尝试了替代方案

仅回复 PASS 或 FAIL: <问题简述>"""

        review_resp = review_llm.invoke([HumanMessage(content=review_prompt)])
        verdict = review_resp.content.strip()
        logger.info("Review #%d (tool_error=%s): %s", count + 1, tool_errors, verdict[:150])

        if verdict.upper().startswith("PASS"):
            return {
                "review_count": count + 1,
                "review_history": prev_history + f"\n[#%d PASS] {verdict[:100]}" % (count + 1),
            }

        # FAIL：生成可操作的改进指令
        fix_instruction = (
            f"[审查未通过 #{count + 1}/3] {verdict}\n"
        )
        if tool_errors:
            fix_instruction += (
                "工具查询未能获取有效结果。请尝试：\n"
                + ("- 换一个搜索词或改用其他工具\n" if len(tool_instances) > 1 else "")
                + "- 缩小或扩大搜索范围重新查询\n"
                + "- 如果所有工具都不可用，告知用户可能的原因并建议检查网络连接\n"
            )
        else:
            fix_instruction += (
                "请重新生成回复，确保：\n"
                "- 严格基于工具返回的信息作答\n"
                "- 先核实再回答，不要猜测\n"
                + ("- 如某个工具未返回结果，尝试用其他工具\n" if len(tool_instances) > 1 else "")
            )

        if count == 2:  # 最后一次机会
            fix_instruction += (
                "\n注意：这是最后一次重试机会。如果仍然无法通过审查，"
                "请在回复中诚实告知用户目前无法获取准确信息，并给出可行的替代建议。"
            )

        feedback = SystemMessage(content=fix_instruction)
        return {
            "messages": [feedback],
            "review_count": count + 1,
            "review_history": prev_history + f"\n[#{count + 1} FAIL] {verdict[:100]}",
        }

    def after_review(state: AgentState):
        """审查后路由"""
        messages = state.get("messages", [])
        if not messages:
            return END
        last = messages[-1]
        if isinstance(last, SystemMessage) and "[审查未通过" in (last.content or ""):
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
            {"messages": initial_messages, "review_count": 0, "review_history": ""},
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
