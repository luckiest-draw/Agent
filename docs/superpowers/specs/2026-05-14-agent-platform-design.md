# Agent Platform Design Specification

**Date:** 2026-05-14  
**Type:** Greenfield Project  
**Purpose:** 企业级多功能 Agent 平台，用于深入学习 Agent 应用开发技术

---

## 1. Overview

### 1.1 项目定位

构建一个生产级的多功能 Agent 平台，支持智能对话、RAG 知识库检索、工作流编排、多 Agent 协作等核心能力。项目采用三层分离架构：TypeScript 前端（React）、Java 业务后端（Spring Boot）、Python AI 引擎层（LangChain）。

### 1.2 核心功能

1. **对话交互** — 多轮对话、流式输出、会话历史、Markdown/代码渲染
2. **知识库管理** — 多格式文档解析（含多模态图片）、向量化、混合检索 RAG
3. **Agent 编排** — Prompt 模板管理、工具绑定、模型配置切换
4. **工作流编排** — 可视化 DAG 拖拽构建、多种节点类型、执行追踪
5. **监控大盘** — Token 消耗、响应延迟、调用量统计、日志查看
6. **多租户 + RBAC** — 租户隔离、角色权限、API Key 管理
7. **多模型支持** — OpenAI / DeepSeek / 通义千问 / 智谱，统一接口切换

---

## 2. Architecture

### 2.1 三层架构

```
React + Vite + TS (Frontend)
        │
        │ HTTP REST + WebSocket
        ▼
Java Spring Boot (Business Backend)
        │
        ├── HTTP Sync ──────────► FastAPI + LangChain (Agent Engine)
        └── RabbitMQ Async ─────► Celery Worker (Agent Engine)
```

### 2.2 通信模式

| 场景 | 方式 | 说明 |
|------|------|------|
| 实时对话 | HTTP → SSE/WebSocket 流式 | Java 代理转发 Python 流式输出到前端 |
| 文档摄入 | RabbitMQ 异步 | 上传后异步解析→分片→向量化，回调通知 |
| 工作流执行 | RabbitMQ 异步 | 长时间 DAG 执行通过 MQ 提交，状态轮询 |
| 简单查询 | HTTP Sync | 检索测试、模型能力查询等 |

### 2.3 数据存储

- **PostgreSQL + Pgvector** — 业务数据 + 向量数据统一存储
- **Redis** — 会话缓存、限流计数器、WebSocket 连接管理
- **RabbitMQ** — 异步任务队列

---

## 3. Java Backend (Spring Boot 3.2 + JDK 17)

### 3.1 模块划分

| 模块 | 职责 | 关键端点 |
|------|------|---------|
| `agent-gateway` | 路由聚合、WebSocket、限流 | `/api/**`, `/ws/**` |
| `agent-auth` | 登录、JWT、RBAC、API Key | `/api/auth/*` |
| `agent-tenant` | 租户CRUD、隔离过滤、配额 | `/api/tenants/*` |
| `agent-knowledge` | 文档上传、解析、分片、向量状态 | `/api/knowledge/*` |
| `agent-conversation` | 会话管理、消息历史、流式代理 | `/api/conversations/*`, `/ws/conversations/{id}/chat` |
| `agent-orchestration` | Prompt模板、工具绑定、模型配置 | `/api/agents/*`, `/api/prompts/*`, `/api/tools/*` |
| `agent-workflow` | 工作流定义、DAG存储 | `/api/workflows/*` |
| `agent-monitor` | 统计大盘、日志查询 | `/api/monitor/*` |

### 3.2 多模态知识库设计

```
文档上传 → 格式识别 → 分支处理
  ├── 文本类(PDF/Word/MD/HTML/TXT) → 提取文本 + 提取内嵌图片
  │     ├── 文本 → TextChunker → Embedding向量化 → Pgvector
  │     └── 图片 → ImageChunker → 存储文件 + 记录关联文档
  │
  └── 纯图片 → 检查模型多模态能力
        ├── 支持(VL模型) → Vision API 生成描述 → 描述文本向量化
        └── 不支持 → 抛出友好异常提示切换模型
```

- 支持的文档格式：PDF、Word (.docx/.doc)、Markdown、HTML、TXT、PNG、JPG
- 图片始终存储，切换模型后可补充向量化
- 模型能力通过 `model_capability.supports_vision` 字段判断

### 3.3 技术依赖

- Spring Boot 3.2, Spring Security, Spring Data JPA
- Spring Data Redis, Spring AMQP (RabbitMQ)
- Spring WebSocket
- PostgreSQL 15 + pgvector extension
- JJWT (JWT), Lombok, MapStruct

---

## 4. Python Agent Engine (FastAPI + LangChain)

### 4.1 模块结构

| 模块 | 职责 |
|------|------|
| `api/` | FastAPI 路由：对话、RAG、工作流、模型信息 |
| `core/model_factory.py` | 多模型工厂，根据配置创建 LLM 实例 |
| `core/model_capability.py` | 模型能力检测：vision、function_call、token_limit |
| `core/embedding_factory.py` | Embedding 模型工厂 |
| `core/memory/` | 短期记忆（窗口） + 长期记忆（摘要压缩） |
| `rag/` | 向量检索 + BM25 混合检索 + Reranker + 文档摄入流水线 |
| `agents/` | ReAct Agent、ToolUse Agent、Supervisor 多 Agent 协调 |
| `tools/` | 工具基类 + 注册器 + 内置工具（搜索、计算、数据库、文件） |
| `workflows/` | DAG 执行引擎 + 节点类型（LLM/Tool/Condition/Code） |
| `monitor/` | 追踪、Token 统计、延迟监控 |
| `worker/` | Celery 异步任务 + 回调通知 |

### 4.2 关键设计决策

| 维度 | 选择 | 原因 |
|------|------|------|
| Agent 框架 | LangChain + LangGraph | LangGraph 的图式编排适合复杂工作流 |
| RAG 策略 | 混合检索（向量 + BM25）+ Reranker | 提升召回率 |
| 多 Agent | Supervisor 模式 | 主 Agent 分发任务给子 Agent |
| 流式输出 | SSE via FastAPI | 前端逐字显示 |
| 异步任务 | Celery + RabbitMQ | 长任务不阻塞主线程 |
| 模型兼容 | 统一 `ChatOpenAI` 兼容接口 | 国内模型兼容 OpenAI 格式 |

### 4.3 技术依赖

- FastAPI, LangChain, LangGraph, langchain-community
- pgvector (Python client), sentence-transformers
- Celery, RabbitMQ client (pika)
- unstructured (文档解析), Pillow (图片处理)
- OpenAI SDK (统一接口)

---

## 5. Frontend (React 18 + Vite + TypeScript)

### 5.1 页面结构

| 页面 | 核心功能 | 技术亮点 |
|------|---------|---------|
| Dashboard | Token消耗曲线、延迟热力图、调用量排行 | Recharts, 实时刷新 |
| Chat | 多轮对话、流式输出、Markdown渲染、会话管理 | SSE, react-markdown |
| Knowledge | 多文件拖拽上传、分片预览、检索测试 | react-dropzone |
| Agent编排 | Prompt编辑器、工具绑定、模型参数、测试区 | Monaco Editor |
| Workflow | 可视化拖拽 DAG、节点连线、配置抽屉、执行日志 | React Flow |
| Tenant | 租户CRUD、配额滑块、API Key管理 | Shadcn Table |
| Auth | 登录、用户管理、角色树、权限矩阵 | RBAC UI |
| Settings | 模型配置(base_url/key)、系统参数 | 配置表单 |

### 5.2 设计风格

- 主题：深色/浅色双模式，默认深色
- 布局：左侧可折叠导航 + 顶栏面包屑 + 内容区
- 配色：Slate 灰底、Indigo 蓝品牌色、Emerald 绿数据指标
- 组件库：Shadcn/ui + Tailwind CSS

### 5.3 技术依赖

- React 18, Vite, TypeScript
- Shadcn/ui, Tailwind CSS, Lucide Icons
- React Flow (工作流画布), Recharts (图表)
- Monaco Editor (Prompt 编辑), react-markdown (消息渲染)
- Zustand (状态管理), Axios (HTTP), react-dropzone (文件上传)

---

## 6. 基础设施

| 组件 | 版本 | 用途 |
|------|------|------|
| PostgreSQL + Pgvector | 15 | 业务数据 + 向量存储 |
| Redis | 7 | 会话缓存、限流 |
| RabbitMQ | 3.12 | 异步消息队列 |
| JDK | 17 | Java 运行时 |
| Python | 3.11+ | Agent 引擎 |

---

## 7. 部署开发

### 7.1 目录结构

```
D:/LearnJava/Agent/
├── agent-frontend/      # React + Vite + TS
├── agent-backend/       # Spring Boot 多模块 Maven 项目
├── agent-engine/        # Python FastAPI + LangChain
├── docker/              # Docker Compose (PostgreSQL, Redis, RabbitMQ)
├── docs/                # 文档
└── README.md
```

### 7.2 启动顺序

1. Docker Compose 启动基础设施（PostgreSQL, Redis, RabbitMQ）
2. 启动 Python Agent Engine（`uvicorn main:app` + Celery Worker）
3. 启动 Java Backend（`mvn spring-boot:run`）
4. 启动前端（`npm run dev`）
