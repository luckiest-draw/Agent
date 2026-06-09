# Tool Registry: 内置工具 + MCP 工具动态发现
from langchain_community.tools import DuckDuckGoSearchRun, WikipediaQueryRun, ArxivQueryRun
from langchain_community.utilities import WikipediaAPIWrapper, ArxivAPIWrapper
import logging

logger = logging.getLogger("tool_registry")

TOOL_DEFS = {
    "web_search": {
        "description": "搜索互联网获取最新信息，返回网页摘要。适用于查新闻、实时数据、公开信息。",
    },
    "wikipedia": {
        "description": "查询维基百科获取百科知识。适用于概念解释、历史事件、人物背景。",
    },
    "arxiv": {
        "description": "搜索 arXiv 学术论文库。适用于科研论文、技术文献查询。",
    },
}

_mcp_tools_cache: list = None


def _create_builtin_tool(name: str):
    """延迟创建内置工具实例"""
    name = name.strip().lower()
    if name == "web_search":
        return DuckDuckGoSearchRun(name="web_search", description=TOOL_DEFS["web_search"]["description"])
    elif name == "wikipedia":
        return WikipediaQueryRun(
            name="wikipedia", api_wrapper=WikipediaAPIWrapper(lang="zh", top_k_results=3),
            description=TOOL_DEFS["wikipedia"]["description"])
    elif name == "arxiv":
        return ArxivQueryRun(
            name="arxiv", api_wrapper=ArxivAPIWrapper(top_k_results=3),
            description=TOOL_DEFS["arxiv"]["description"])
    return None


async def _ensure_mcp_tools():
    """确保 MCP 工具已加载（懒加载 + 缓存）"""
    global _mcp_tools_cache
    if _mcp_tools_cache is not None:
        return _mcp_tools_cache
    from tools.mcp_manager import load_mcp_tools
    _mcp_tools_cache = await load_mcp_tools()
    return _mcp_tools_cache


async def get_tools(tool_names: list[str]) -> list:
    """根据工具名列表返回 BaseTool 实例（内置 + MCP），自动去重"""
    tools = []
    seen_names = set()
    mcp_tools = await _ensure_mcp_tools()
    mcp_by_name = {t.name: t for t in mcp_tools}

    for name in tool_names:
        name = name.strip().lower()
        if name in seen_names:
            continue
        if name in TOOL_DEFS:
            tool = _create_builtin_tool(name)
            if tool:
                tools.append(tool)
                seen_names.add(name)
            continue
        if name in mcp_by_name:
            tools.append(mcp_by_name[name])
            seen_names.add(name)
            continue
        logger.warning("Unknown tool: %s, skipping", name)

    return tools


def list_tools() -> list[dict]:
    """列出内置工具（同步，不触发 MCP 加载）"""
    return [{"name": k, "description": v["description"]} for k, v in TOOL_DEFS.items()]


async def list_all_tools() -> list[dict]:
    """列出所有工具（内置 + MCP）"""
    builtin = [{"name": k, "description": v["description"], "source": "builtin"}
               for k, v in TOOL_DEFS.items()]
    mcp_tools = await _ensure_mcp_tools()
    mcp = [{"name": t.name, "description": t.description or "", "source": "mcp"}
           for t in mcp_tools]
    return builtin + mcp
