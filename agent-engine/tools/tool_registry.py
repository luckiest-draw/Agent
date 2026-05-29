# Tool Registry: maps tool names to LangChain BaseTool instances (lazy init)
from langchain_community.tools import DuckDuckGoSearchRun, WikipediaQueryRun, ArxivQueryRun
from langchain_community.utilities import WikipediaAPIWrapper, ArxivAPIWrapper
import logging

logger = logging.getLogger("tool_registry")

TOOL_DEFS = {
    "web_search": {
        "name": "web_search",
        "description": "搜索互联网获取最新信息，返回网页摘要。适用于查新闻、实时数据、公开信息。",
    },
    "wikipedia": {
        "name": "wikipedia",
        "description": "查询维基百科获取百科知识。适用于概念解释、历史事件、人物背景。",
    },
    "arxiv": {
        "name": "arxiv",
        "description": "搜索 arXiv 学术论文库。适用于科研论文、技术文献查询。",
    },
}


def _create_tool(name: str):
    """延迟创建工具实例，避免 import 时校验依赖"""
    if name == "web_search":
        return DuckDuckGoSearchRun(
            name="web_search",
            description=TOOL_DEFS["web_search"]["description"],
        )
    elif name == "wikipedia":
        return WikipediaQueryRun(
            name="wikipedia",
            api_wrapper=WikipediaAPIWrapper(lang="zh", top_k_results=3),
            description=TOOL_DEFS["wikipedia"]["description"],
        )
    elif name == "arxiv":
        return ArxivQueryRun(
            name="arxiv",
            api_wrapper=ArxivAPIWrapper(top_k_results=3),
            description=TOOL_DEFS["arxiv"]["description"],
        )
    return None


def get_tools(tool_names: list[str]) -> list:
    tools = []
    for name in tool_names:
        name = name.strip().lower()
        if name in TOOL_DEFS:
            tool = _create_tool(name)
            if tool:
                tools.append(tool)
        else:
            logger.warning("Unknown tool: %s, skipping", name)
    return tools


def list_tools() -> list[dict]:
    return [{"name": k, "description": v["description"]} for k, v in TOOL_DEFS.items()]
