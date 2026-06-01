# MCP Manager: 管理与外部 MCP Server 的连接和工具发现
from typing import Optional
import logging
import threading

logger = logging.getLogger("mcp_manager")

# 可配置的 MCP 服务器列表
# transport="stdio": 本地子进程方式启动 MCP Server
# transport="sse": 远程 HTTP SSE 方式连接
MCP_SERVER_CONFIGS = {
    "filesystem": {
        "command": "npx",
        "args": ["-y", "@modelcontextprotocol/server-filesystem",
                 "."],  # 允许访问当前工作目录
        "transport": "stdio",
        "description": "读写本地文件系统",
        "enabled": True,
    },
    # 示例：Web Search MCP（需要 Node.js）
    # "websearch": {
    #     "command": "npx",
    #     "args": ["-y", "@iflow-mcp/open-websearch"],
    #     "transport": "stdio",
    #     "description": "联网搜索（多搜索引擎）",
    #     "enabled": False,
    # },
}

_mcp_tools_cache: list = []
_cache_lock = threading.Lock()
_client = None


def _build_client_config() -> dict:
    """构建 MultiServerMCPClient 所需的配置"""
    config = {}
    for name, cfg in MCP_SERVER_CONFIGS.items():
        if not cfg.get("enabled", True):
            continue
        if cfg["transport"] == "stdio":
            config[name] = {
                "command": cfg["command"],
                "args": cfg["args"],
                "transport": "stdio",
            }
        elif cfg["transport"] == "sse":
            config[name] = {
                "url": cfg["url"],
                "transport": "sse",
            }
    return config


async def load_mcp_tools() -> list:
    """加载所有已启用 MCP Server 的工具（带 10 秒超时和缓存）"""
    global _mcp_tools_cache, _client

    with _cache_lock:
        if _mcp_tools_cache:
            return list(_mcp_tools_cache)

    config = _build_client_config()
    if not config:
        logger.info("No MCP servers enabled")
        return []

    from langchain_mcp_adapters.client import MultiServerMCPClient
    import asyncio

    try:
        _client = MultiServerMCPClient(config)
        tools = await asyncio.wait_for(_client.get_tools(), timeout=10.0)
        with _cache_lock:
            _mcp_tools_cache = list(tools)
        logger.info("Loaded %d tools from %d MCP server(s): %s",
                     len(tools), len(config), list(config.keys()))
        for t in tools:
            logger.info("  MCP tool: %s (%s)", t.name, t.description[:80] if t.description else "no desc")
        return list(_mcp_tools_cache)
    except asyncio.TimeoutError:
        logger.warning("MCP server connection timed out (10s), tools unavailable")
        return []
    except Exception as e:
        logger.warning("Failed to load MCP tools: %s", e)
        return []


def get_mcp_tool_names() -> list[str]:
    """返回已缓存的 MCP 工具名列表（非阻塞）"""
    with _cache_lock:
        return [t.name for t in _mcp_tools_cache]


def list_mcp_servers() -> list[dict]:
    """列出已配置的 MCP 服务器信息"""
    return [
        {"name": name, "description": cfg.get("description", ""),
         "enabled": cfg.get("enabled", True),
         "transport": cfg.get("transport", "stdio")}
        for name, cfg in MCP_SERVER_CONFIGS.items()
    ]


def invalidate_cache():
    """清除缓存，下次调用 load_mcp_tools() 时重新连接"""
    global _mcp_tools_cache
    with _cache_lock:
        _mcp_tools_cache = []
    logger.info("MCP tool cache invalidated")
