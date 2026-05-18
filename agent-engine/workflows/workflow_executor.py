# Workflow Executor: LangGraph-based DAG workflow engine
from typing import TypedDict, List, Annotated, Literal
from langgraph.graph import StateGraph, END
from models.model_manager import create_llm
from langchain_core.messages import HumanMessage, SystemMessage, AIMessage


class WorkflowState(TypedDict):
    """工作流执行状态"""
    input: str
    messages: Annotated[list, "append"]
    current_step: int
    final_output: str
    error: str


def create_llm_node(model: str, system_prompt: str, temperature: float = 0.7):
    """创建LLM调用节点"""
    def node_func(state: WorkflowState) -> dict:
        try:
            llm = create_llm(model_name=model, temperature=temperature)
            messages = [SystemMessage(content=system_prompt)]
            # 添加上下文消息
            for msg in state.get("messages", []):
                if msg.get("role") == "user":
                    messages.append(HumanMessage(content=msg["content"]))
                elif msg.get("role") == "assistant":
                    messages.append(AIMessage(content=msg["content"]))
            messages.append(HumanMessage(content=state["input"]))
            response = llm.invoke(messages)
            content = response.content if hasattr(response, "content") else str(response)
            return {
                "messages": [{"role": "assistant", "content": content}],
                "final_output": content,
                "current_step": state.get("current_step", 0) + 1,
            }
        except Exception as e:
            return {"error": str(e), "final_output": f"Error: {e}"}
    return node_func


def create_condition_node(condition_field: str, routing_map: dict):
    """创建条件路由节点"""
    def node_func(state: WorkflowState) -> Literal[tuple(routing_map.keys())]:
        value = state.get(condition_field, "")
        for key, keywords in routing_map.items():
            for kw in keywords:
                if kw.lower() in str(value).lower():
                    return key
        return list(routing_map.keys())[0]  # default first route
    return node_func


def execute_workflow(
    workflow_def: dict,  # {nodes: [...], edges: [...], input: str}
    model: str = "deepseek-chat",
) -> dict:
    """执行工作流DAG"""
    nodes = workflow_def.get("nodes", [])
    edges = workflow_def.get("edges", [])
    input_text = workflow_def.get("input", "")

    # 构建LangGraph
    graph = StateGraph(WorkflowState)

    # 添加节点
    for node in nodes:
        node_type = node.get("nodeType", "agent")
        node_id = str(node.get("id", ""))
        label = node.get("label", "")
        config = node.get("nodeConfig", {})
        system_prompt = config.get("systemPrompt", "")

        if node_type in ("agent", "start"):
            graph.add_node(node_id, create_llm_node(model, system_prompt))
        elif node_type == "end":
            graph.add_node(node_id, lambda s: {"final_output": s.get("final_output", s["input"])})

    # 添加边
    for edge in edges:
        source = str(edge.get("sourceNodeId", ""))
        target = str(edge.get("targetNodeId", ""))
        if source and target:
            graph.add_edge(source, target)

    # 设置入口
    if nodes:
        entry = str(nodes[0].get("id", ""))
        graph.set_entry_point(entry)

    # 编译执行
    app = graph.compile()
    result = app.invoke({"input": input_text, "messages": [], "current_step": 0, "final_output": ""})
    return result
