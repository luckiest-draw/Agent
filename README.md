# Agent Platform

企业级多功能 AI Agent 平台（学习项目）。支持 RAG 知识库、多轮对话、Agent 编排、可视化工作流 (DAG)、监控看板、多租户 RBAC 和多模态文档处理。

## 系统架构

```
┌─────────────────────────────────────────────────────────┐
│                   前端 (React + Vite)                     │
│   React 18 / TypeScript / Shadcn/ui / Tailwind / Zustand │
└──────────┬──────────────────────┬───────────────────────┘
           │ HTTP (REST)          │ WebSocket / SSE
┌──────────▼──────────────────────▼───────────────────────┐
│              网关 (Spring Boot 3.2.5)                     │
│  CORS / WebSocket / RabbitMQ / Security / 多租户          │
├──────────┬──────────┬──────────┬──────────┬─────────────┤
│   认证   │   租户   │ 知识库   │   会话   │    编排      │
│   JWT    │   配额   │ 文档/RAG │   聊天   │  智能体/工具  │
├──────────┴──────────┴──────────┴──────────┴─────────────┤
│           工作流 (DAG)       │      监控 (API/Token)      │
└─────────────────────────────┴────────────────────────────┘
           │ RabbitMQ (异步任务)  │ HTTP (流式传输)
┌──────────▼──────────────────────────────────────────────┐
│               AI 引擎 (Python FastAPI)                    │
│  LangChain / LangGraph / Celery / Pgvector                │
│  多模型: DeepSeek · GPT-4o · Qwen · GLM-4                 │
└─────────────────────────────────────────────────────────┘
           │        │          │
┌──────────▼──┐ ┌──▼───┐ ┌───▼──────────┐
│  PostgreSQL  │ │Redis │ │  RabbitMQ     │
│  + pgvector  │ │      │ │               │
└──────────────┘ └──────┘ └───────────────┘
```

## 技术栈

| 层级 | 技术 |
|------|------|
| **前端** | React 18, TypeScript, Vite, Tailwind CSS, Shadcn/ui, Zustand, React Flow, Recharts |
| **后端** | Java 17, Spring Boot 3.2.5, Spring Security, JWT (jjwt 0.12.5), MyBatis-Plus 3.5.9, Spring AMQP |
| **AI 引擎** | Python 3.10+, FastAPI, LangChain, LangGraph, Celery, pgvector |
| **数据库** | PostgreSQL 15 + pgvector 扩展, Redis 7 |
| **消息队列** | RabbitMQ 3.12 |
| **基础设施** | Docker Compose |

## 项目结构

```
Agent/
├── docker/
│   └── docker-compose.yml          # PostgreSQL+pgvector, Redis, RabbitMQ
├── agent-backend/                  # Java Spring Boot 多模块
│   ├── pom.xml                     # 父 POM（9 个模块）
│   ├── agent-common/               # Result, BaseEntity, 异常处理, MyBatis-Plus 配置
│   ├── agent-auth/                 # 用户/角色/权限, JWT, Spring Security
│   ├── agent-tenant/               # 租户, 配额, 租户拦截器
│   ├── agent-knowledge/            # 文档解析, 分块, 多模态
│   ├── agent-conversation/         # 会话, 消息, SSE 流式对话
│   ├── agent-orchestration/        # Agent 配置, 提示词模板, 工具定义
│   ├── agent-workflow/             # 工作流定义, 节点, 连线 (DAG)
│   ├── agent-monitor/              # API 日志, Token 用量监控
│   └── agent-gateway/              # Spring Boot 入口, CORS/WS/MQ 配置
├── agent-engine/                   # Python AI 引擎
│   ├── main.py                     # FastAPI 入口
│   ├── config.py                   # Pydantic 配置
│   ├── models/
│   │   └── model_manager.py        # 多模型路由 (OpenAI 兼容)
│   ├── rag/
│   │   ├── vector_store.py         # Pgvector 集成
│   │   └── retriever.py            # RAG 检索 + 提示词构建
│   ├── agents/
│   │   └── chat_agent.py           # 多轮对话 + 流式输出
│   ├── workflows/
│   │   └── workflow_executor.py    # LangGraph DAG 执行器
│   └── tasks/
│       ├── celery_app.py           # Celery 配置
│       ├── doc_process.py          # 异步文档处理 + 图片描述
│       └── workflow_exec.py        # 异步工作流执行
└── agent-frontend/                 # React TypeScript 前端
    ├── src/
    │   ├── api/client.ts           # HTTP API 客户端
    │   ├── store/appStore.ts       # Zustand 全局状态
    │   ├── components/layout/      # 侧边栏, 顶栏
    │   └── pages/                  # 仪表盘/聊天/知识库/工作流/监控/设置/登录
    ├── vite.config.ts
    ├── tailwind.config.js
    └── package.json
```

## 功能概览

### 多功能
- **RAG 知识库** — 上传 PDF/Word/Markdown/HTML/TXT/图片，自动解析、分块、向量化存入 Pgvector
- **多轮对话** — SSE 流式输出，RAG 增强，支持多模型切换
- **Agent 编排** — 可配置的智能体，支持系统提示词、工具和模型选择
- **可视化工作流** — React Flow 拖拽式 DAG 画布，节点类型：开始/智能体/工具/条件/结束
- **监控看板** — Token 用量图表、API 调用日志、系统状态
- **多租户 RBAC** — 租户隔离、JWT 认证、用户/角色/权限管理

### 多模态
- **文档类型**: PDF、Word (.docx)、Markdown、HTML、TXT
- **图片支持**: JPG、PNG、GIF、BMP — 解析为图片块，标记需要视觉模型处理
- **视觉模型**: GPT-4o、GPT-4 Turbo、GLM-4v、Qwen-VL-Plus/Max
- **优雅降级**: 模型不支持视觉时自动回退到纯文本处理

### 多模型
支持 OpenAI 兼容 API: DeepSeek、GPT-4o、Qwen、GLM-4

### 混合通信
- **HTTP (同步)**: REST API + SSE 流式对话
- **RabbitMQ (异步)**: 文档处理、工作流执行通过 Celery 调度

## 快速开始

### 前置条件
- Java 17+, Maven 3.9+
- Python 3.10+, Node.js 18+
- Docker Desktop

### 1. 启动基础设施

```bash
cd docker
docker compose up -d
```

启动服务: PostgreSQL:5432, Redis:6379, RabbitMQ:15672 (管理界面)

### 2. 创建数据库

连接 PostgreSQL 执行:

```sql
CREATE DATABASE agent_platform;
CREATE EXTENSION vector;  -- pgvector
```

### 3. 启动 Java 后端

```bash
cd agent-backend
mvn clean compile
mvn -pl agent-gateway spring-boot:run
```

网关启动在 **http://localhost:9090**。

**默认账号**（首次启动自动创建）:
- 用户名: `admin`，密码: `admin123`

### 4. 配置 API Key

编辑 `agent-engine/.env`，**至少填入 DeepSeek API Key**（其他模型可留空）:

```
DEEPSEEK_API_KEY=sk-xxxxxxxx
```

### 5. 启动 Python AI 引擎

```bash
cd agent-engine
pip install -r requirements.txt
python main.py
```

引擎启动在 **http://localhost:8000**。

> **Windows 注意**: 如启动后立刻 shutdown，通常是 reload 监控到 `__pycache__` 等文件变化导致。已在 `main.py` 中配置 `reload_dirs` + `reload_excludes` 解决，正常情况不会再出现。

### 6. 启动 Celery Worker（可选）

```bash
cd agent-engine
celery -A tasks.celery_app worker --loglevel=info
```

### 7. 启动前端

```bash
cd agent-frontend
npm install
npm run dev
```

前端启动在 **http://localhost:3000**。

## API 端点

### 认证
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 用户名密码登录，返回 JWT |
| POST | `/api/auth/register` | 注册新用户 |

### 租户
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/tenants` | 租户列表 |
| POST | `/api/tenants` | 创建租户 |
| PUT | `/api/tenants/{id}` | 更新租户 |
| DELETE | `/api/tenants/{id}` | 删除租户 |
| GET | `/api/tenants/{id}/quota` | 查看配额 |
| PUT | `/api/tenants/{id}/quota` | 更新配额 |

### 知识库
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/knowledge/documents/upload` | 上传文档 (multipart) |

### 会话（带记忆：滑动窗口 + Token 压缩 + PII 脱敏 + 话题检测）
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/conversations` | 会话列表 |
| POST | `/api/conversations` | 创建会话 |
| POST | `/api/conversations/stream` | SSE 流式对话（无会话ID） |
| POST | `/api/conversations/{id}/stream` | SSE 流式对话（带上下文记忆+话题检测） |
| GET | `/api/conversations/{id}/messages` | 历史消息 |

### 编排
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/orchestration/agents` | 智能体配置列表 |
| POST | `/api/orchestration/agents` | 创建智能体配置 |
| PUT | `/api/orchestration/templates/{id}` | 更新提示词模板 |
| POST | `/api/orchestration/tools` | 创建工具定义 |

### 工作流
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/workflows` | 工作流列表 |
| POST | `/api/workflows` | 创建工作流 |
| GET | `/api/workflows/{id}/dag` | 获取 DAG（节点+连线） |
| PUT | `/api/workflows/{id}/dag` | 保存 DAG |

### 监控
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/monitor/api-logs` | API 调用日志 |
| GET | `/api/monitor/token-usage?date=2024-01-01` | 按日期查询 Token 用量 |

### AI 引擎
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health` | 健康检查 |
| POST | `/chat/stream` | SSE 流式对话 |
| POST | `/chat/rag` | RAG 增强流式对话 |
| POST | `/rag/retrieve` | 检索文档 |
| POST | `/rag/embed` | 文本向量化 |
| GET | `/models/supported` | 支持的模型列表 |
| POST | `/workflow/execute` | 同步执行工作流 |

## 环境变量

### Java 后端 (`application.yml`)
| 变量 | 默认值 | 说明 |
|------|--------|------|
| `jwt.secret` | (base64 密钥) | JWT 签名密钥 |
| `jwt.expiration` | 86400000 | Token 过期时间 (毫秒) |
| `ai.engine.url` | http://localhost:8000 | Python 引擎地址 |

### Python 引擎 (`.env`)
| 变量 | 说明 |
|------|------|
| `DEEPSEEK_API_KEY` | DeepSeek API 密钥 |
| `OPENAI_API_KEY` | OpenAI API 密钥 |
| `QWEN_API_KEY` | 通义千问 API 密钥 |
| `GLM_API_KEY` | 智谱 GLM-4 API 密钥 |

### Docker 服务
| 服务 | 端口 | 账号/密码 |
|------|------|-----------|
| PostgreSQL | 5432 | agent / agent123 |
| Redis | 6379 | — |
| RabbitMQ | 5672 (AMQP) / 15672 (管理界面) | agent / agent123 |

## 设计决策

- **使用 Lombok + MyBatis-Plus**: 实体类用 `@Getter/@Setter`，DTO 用 `@Data`，减少样板代码。持久层用 MyBatis-Plus `BaseMapper` + `LambdaQueryWrapper` 替代 JPA。注意需要 `JAVA_HOME` 指向 JDK 17（JDK 24 下 Lombok 1.18.36 不兼容）
- **会话记忆系统**: Redis + PostgreSQL 两级缓存，20 轮滑动窗口（LTRIM），Token 超 4000 自动压缩旧消息为摘要，PII 脱敏（手机/身份证/邮箱/银行卡进 Redis 前脱敏），语义话题检测自动分子会话
- **统一错误码**: `ErrorCode` 枚举集中管理业务错误码，`BusinessException` 携带 ErrorCode，异常处理更规范
- **Schema 管理**: 用 `schema.sql` 定义 DDL（`spring.sql.init.mode=always`），替代 Hibernate 的 `ddl-auto`
- **Pgvector 部署在 PostgreSQL**: 单库同时管理关系数据和向量检索
- **Python 做 AI 引擎**: 充分利用 LangChain/LangGraph 生态；Java 负责业务逻辑
- **OpenAI 兼容 API**: 所有模型通过 ChatOpenAI 统一路由，切换模型无需改代码
- **简化登录**: 学习项目不用 OAuth2/OIDC，采用 JWT 用户名密码认证
