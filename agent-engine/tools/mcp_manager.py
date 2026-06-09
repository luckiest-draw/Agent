# MCP Manager: 管理与外部 MCP Server 的连接和工具发现
from typing import Optional
import logging
import threading

logger = logging.getLogger("mcp_manager")

# 可配置的 MCP 服务器列表
# transport="stdio": 本地子进程方式启动 MCP Server
# transport="sse": 远程 HTTP SSE 方式连接
MCP_SERVER_CONFIGS = {
    # 文件系统：读写本地文件
    "filesystem": {
        "command": "npx",
        "args": ["-y", "@modelcontextprotocol/server-filesystem", "."],
        "transport": "stdio",
        "description": "读写本地文件系统",
        "enabled": False,  # Python 3.14 兼容性问题，暂时关闭
    },
    # GitHub：PR/Issue/代码操作，需设置 GITHUB_PERSONAL_ACCESS_TOKEN 环境变量
    "github": {
        "command": "npx",
        "args": ["-y", "@modelcontextprotocol/server-github"],
        "transport": "stdio",
        "env": {
            "GITHUB_PERSONAL_ACCESS_TOKEN": "${GITHUB_PERSONAL_ACCESS_TOKEN}",
        },
        "description": "GitHub API：搜索代码、管理PR/Issue、查看仓库",
        "enabled": False,  # Python 3.14 兼容性问题，暂时关闭
    },
    # PostgreSQL：执行数据库查询
    "postgres": {
        "command": "npx",
        "args": ["-y", "@modelcontextprotocol/server-postgres",
                 "postgresql://agent:agent123@localhost:5432/agent_platform"],
        "transport": "stdio",
        "description": "查询 PostgreSQL 数据库（只读）",
        "enabled": False,  # 开启前确保数据库在运行
    },
    # Tavily Search：AI 优化的联网搜索（免费 1000次/月）
    # ▶ 注册获取 API Key: https://tavily.com
    # "tavily_search": {
    #     "command": "npx",
    #     "args": ["-y", "tavily-mcp"],
    #     "transport": "stdio",
    #     "env": {"TAVILY_API_KEY": "${TAVILY_API_KEY}"},
    #     "description": "Tavily 搜索（AI 优化，结果直接可用）",
    #     "enabled": False,
    # },
    # SearXNG：自托管搜索引擎聚合器，完全免费无需API Key
    # ▶ 启动: docker run -d -p 8888:8888 searxng/searxng
    # "searxng": {
    #     "command": "python",
    #     "args": ["-m", "mcp_server_freesearch"],
    #     "transport": "stdio",
    #     "env": {"SEARXNG_URL": "http://localhost:8888"},
    #     "description": "SearXNG 自托管搜索（完全免费，多引擎聚合）",
    #     "enabled": False,
    # },
    # Sequential Thinking：复杂推理（结构化分步思考）
    # "sequential_thinking": {
    #     "command": "npx",
    #     "args": ["-y", "@anthropic-ai/mcp-server-sequential-thinking"],
    #     "transport": "stdio",
    #     "description": "结构化分步思考，适合复杂问题推理",
    #     "enabled": False,
    # },
}

_mcp_tools_cache: list = []
_cache_lock = threading.Lock()
_client = None


def _build_client_config() -> dict:
    """构建 MultiServerMCPClient 所需的配置，解析环境变量占位符"""
    import os
    import re

    def resolve_env(value: str) -> str:
        """替换 ${VAR_NAME} 为实际环境变量值"""
        pattern = re.compile(r'\$\{(\w+)\}')
        matches = pattern.findall(value)
        for var in matches:
            env_val = os.environ.get(var, "")
            if not env_val:
                logger.warning("Env var %s not set for MCP config", var)
            value = value.replace(f"${{{var}}}", env_val)
        return value

    config = {}
    for name, cfg in MCP_SERVER_CONFIGS.items():
        if not cfg.get("enabled", True):
            continue
        entry = {"transport": cfg["transport"]}
        if cfg["transport"] == "stdio":
            entry["command"] = cfg["command"]
            entry["args"] = cfg["args"]
            if "env" in cfg:
                entry["env"] = {k: resolve_env(v) for k, v in cfg["env"].items()}
        elif cfg["transport"] == "sse":
            entry["url"] = cfg["url"]
        config[name] = entry
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
    except ImportError as e:
        logger.warning("MCP adapter unavailable: %s", e)
        return []
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
