# Tool Registry: maps tool names to LangChain BaseTool instances
from langchain_community.tools import DuckDuckGoSearchRun, WikipediaQueryRun, ArxivQueryRun
from langchain_community.utilities import WikipediaAPIWrapper, ArxivAPIWrapper
import logging

logger = logging.getLogger("tool_registry")

BUILTIN_TOOLS = {
    "web_search": DuckDuckGoSearchRun(
        name="web_search",
        description="搜索互联网获取最新信息，返回网页摘要。适用于查新闻、实时数据、公开信息。"
    ),
    "wikipedia": WikipediaQueryRun(
        name="wikipedia",
        api_wrapper=WikipediaAPIWrapper(lang="zh", top_k_results=3),
        description="查询维基百科获取百科知识。适用于概念解释、历史事件、人物背景。"
    ),
    "arxiv": ArxivQueryRun(
        name="arxiv",
        api_wrapper=ArxivAPIWrapper(top_k_results=3),
        description="搜索 arXiv 学术论文库。适用于科研论文、技术文献查询。"
    ),
}


def get_tools(tool_names: list[str]) -> list:
    """根据工具名列表返回 LangChain BaseTool 列表"""
    tools = []
    for name in tool_names:
        name = name.strip().lower()
        if name in BUILTIN_TOOLS:
            tools.append(BUILTIN_TOOLS[name])
        else:
            logger.warning("Unknown tool: %s, skipping", name)
    return tools


def list_tools() -> list[dict]:
    """列出所有内置工具的名称和描述"""
    return [
        {"name": name, "description": tool.description}
        for name, tool in BUILTIN_TOOLS.items()
    ]
