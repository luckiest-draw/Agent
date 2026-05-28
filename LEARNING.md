# Agent Platform 学习文档

## 项目概述

这是一个**企业级多功能 AI Agent 平台**，三端分离架构：

| 端 | 技术栈 | 端口 | 职责 |
|----|--------|------|------|
| 前端 | React 18 + TypeScript + Vite + Tailwind + Shadcn/ui | 3000 | 用户界面 |
| 后端 | Java 17 + Spring Boot 3.2.5 + Maven 多模块 | 9090 | 业务逻辑、权限、数据持久化 |
| AI 引擎 | Python 3.10+ + FastAPI + LangChain + LangGraph + PyMuPDF | 8000 | AI 推理、RAG、多模态(视觉/语音)、工作流执行 |

依赖基础设施：PostgreSQL+pgvector（向量库）、Redis（缓存）、RabbitMQ（消息队列），全部通过 Docker Compose 管理。

---

## 学习路线图

建议按以下顺序学习，每阶段 1-3 天，由浅入深、由基础到复合。

### 第一阶段：基础支撑层（1 天）

**目标**：理解项目的骨架——公共组件、基础设施、模块结构。

#### 1.1 Docker 基础设施
- 文件：`docker/docker-compose.yml`
- 三个服务：PostgreSQL（带 pgvector 插件）、Redis、RabbitMQ
- 启动：`docker compose up -d`
- 重点理解：pgvector 是什么、为什么向量数据库要和业务库放一起

#### 1.2 Maven 多模块结构
- 文件：`agent-backend/pom.xml`（父 POM）
- 9 个子模块，父 POM 统一管理依赖版本
- 模块间依赖关系：
  ```
  agent-common              ← 所有模块都依赖它
      ├── agent-auth        ← 认证模块
      ├── agent-tenant      ← 租户模块
      ├── agent-knowledge   ← 知识库模块
      ├── agent-conversation ← 会话模块
      ├── agent-orchestration ← 编排模块
      ├── agent-workflow     ← 工作流模块
      ├── agent-monitor      ← 监控模块
      └── agent-gateway      ← 网关模块（入口，依赖所有其他模块）
  ```
- `agent-gateway` 是唯一的启动模块，`@SpringBootApplication(scanBasePackages = "com.agent")` 会扫描所有模块

#### 1.3 agent-common：公共模块
- `Result<T>` — 统一 API 返回体 `{ code, message, data }`
- `BaseEntity` — MyBatis-Plus 实体基类，提供 `id`（`@TableId(type=IdType.AUTO)`）、`createdAt`、`updatedAt`，通过 `MyMetaObjectHandler` 自动填充时间
- `ErrorCode` — 统一错误码枚举（SUCCESS/400/401/403/404/409/500 等），集中管理业务异常码
- `BusinessException` — 业务异常类，构造函数接受 `ErrorCode` 枚举，语义化抛异常
- `GlobalExceptionHandler` — `@RestControllerAdvice` 全局异常处理，捕获 `BusinessException` 和未知异常

**关键设计决策**：使用 Lombok，实体类用 `@Getter/@Setter`、DTO 用 `@Data` 减少样板代码。Lombok 作为直接依赖添加到每个模块。注意 `JAVA_HOME` 必须指向 JDK 17，JDK 24 下 Lombok 1.18.36 不兼容。

---

### 第二阶段：认证与安全（1 天）

**目标**：理解 Spring Security + JWT 认证流程。

#### 2.1 实体层
- `User` (`sys_user`) — 用户，通过 `@TableField(exist=false)` 持有角色集合，多对多关系由 `UserMapper.selectRolesByUserId()` 和 `sys_user_role` 中间表手动管理
- `Role` (`sys_role`) — 角色，同样通过 `@TableField(exist=false)` 持有权限集合
- `Permission` (`sys_permission`) — 权限
- 中间表：`sys_user_role`、`sys_role_permission`

#### 2.2 JWT 认证链
```
请求 → JwtFilter（提取 Token）→ SecurityFilterChain（鉴权规则）→ Controller
```

关键文件：
- `JwtUtil` — JWT 生成和解析，使用 jjwt 0.12.5 库，claims 中携带 userId、username、roles
- `JwtFilter` — `OncePerRequestFilter`，Spring 容器注入 `JwtUtil`，从 `Authorization: Bearer xxx` 头提取 token，校验后设置 `SecurityContext`
- `SecurityConfig` — 安全配置：
  - 关闭 CSRF（API 不需要）
  - 无状态 Session（`SessionCreationPolicy.STATELESS`）
  - 白名单路径：`/api/auth/login`、`/api/auth/register`、`/api/conversations/stream`、`/ws/**`
  - 其余全部需要认证

#### 2.3 认证流程
```
POST /api/auth/login  { username, password }
    → AuthService.login()
        → 查用户 → 验密码(BCrypt) → 生成JWT
        → 返回 { token, user: {id, username, roles} }

POST /api/auth/register  { username, password, email }
    → AuthService.register()
        → 校验唯一性 → 加密密码 → 分配USER角色 → 生成JWT
        → 注册即登录，直接返回token
```

#### 2.4 启动初始化
- `DataInitializer` — `CommandLineRunner`，启动时自动创建 `ADMIN` 角色和 `admin/admin123` 管理员账号

---

### 第三阶段：核心业务模块（2 天）

**目标**：理解每个业务模块的 Entity、Mapper（MyBatis-Plus BaseMapper）、Service、Controller 分层结构。

#### 3.1 多租户 (agent-tenant)
- `Tenant` (`sys_tenant`) — 租户实体：name、description、apiKey、enabled
- `TenantQuota` — 租户配额
- `TenantInterceptor` — **理解 ThreadLocal 租户隔离**：
  - 前端请求头带 `X-Tenant-Id`
  - 拦截器提取并存入 `ThreadLocal<Long>`
  - 后续可通过 `TenantInterceptor.getCurrentTenantId()` 获取
  - 请求结束后自动清理

#### 3.2 会话管理 (agent-conversation)
- `Conversation` (`conv_conversation`) — 会话：title、userId、tenantId、agentConfigId、messageCount、parentId、summary
- `Message` (`conv_message`) — 消息：conversationId、role(user/assistant/system)、content、tokenCount

**会话记忆系统**（两级缓存 + 多层处理）：

```
用户消息
    │
    ├── PiiMasker         → 正则脱敏（手机/身份证/邮箱/银行卡）
    ├── TopicDetector     → 三层漏斗话题检测
    │     ├── 第1层：关键词交集 > 50% → 同话题，跳过
    │     ├── 第2层：短句/闲聊 → 跳过
    │     ├── 第3层：Embedding + 余弦相似度 → 判话题切换
    │     └── 话题切换 → 自动创建子会话 (parentId 关联)
    ├── MemoryPipeline    → 上下文构建编排器
    │     ├── MessageCacheService → Redis List 读写 + TTL 24h
    │     ├── TokenCounter → 中英文混排估算 token 数
    │     └── CompressionService → 超 4000 token 压缩旧消息为摘要
    └── StreamingProxy    → 转发到 Python AI 引擎
```

**Redis 存储结构**：
| Key | 类型 | 说明 |
|-----|------|------|
| `chat:history:{convId}` | List (JSON) | 最近 20 轮（40条）消息，RPUSH + LTRIM |
| `chat:summary:{convId}` | String | LLM 生成的压缩摘要 |
| `chat:topic:{convId}` | String | 运行中话题向量 (JSON float[]) |
| `chat:keywords:{convId}` | Set | 最近消息关键词（第1层粗筛用） |

**核心流程——流式对话（带记忆）**：
```
前端 POST /api/conversations/{id}/stream  { message, modelName }
    → ConversationController.stream()
        1. PiiMasker.mask() → 脱敏
        2. TopicDetector.detect() → 三层漏斗判话题
           └─ 话题切换 → 创建子会话 (parentId=原id)
        3. ChatService.saveUserMessage() → DB + Redis 双写
        4. MemoryPipeline.buildContext()
           → Redis 拉历史 → 滑动窗口截断 → Token 超限则压缩
        5. StreamingProxy.streamChat() → 拼上下文 → Python 引擎
        6. 回调收集完整 AI 回复
        7. afterResponse: 保存回复 → DB + Redis 双写 + 更新统计
    → SSE 流式返回前端逐字渲染
```

关键文件：
- `StreamingProxy` — 使用 `HttpURLConnection` 连接 Python 引擎，Jackson `ObjectMapper` 构建 JSON
- `ChatService` — 保存用户/助手消息到数据库
- `ChatWebSocketHandler` — WebSocket 备用通道

> **调试经验**：Spring SseEmitter 发送的 SSE 格式是 `data:{...}`（无空格），前端解析需用 `line.startsWith('data:')` 而非 `line.startsWith('data: ')`

**多模态聊天功能**：

**图片上传 + 视觉问答**：
```
[浏览器] 用户选择图片 → ImageUploader 立即上传到 Java
    POST /api/conversations/upload-image (multipart)
    → ImageUploadService.saveImage()
        → 校验类型 (JPEG/PNG/GIF/WebP/BMP) + 大小 (≤10MB)
        → 保存到 uploads/chat-images/{date}/{uuid}.{ext}
        → 返回 imageUrl
[浏览器] 用户发送消息 + imageUrl
    → StreamingProxy 将 imageUrl 传给 Python 引擎
    → Python multimodal.py.encode_image_to_base64(imageUrl)
        → 先在项目根目录下找文件，再尝试 agent-gateway/ 子目录
        → 读取文件 → Base64 编码 → data:image/xxx;base64,...
    → build_multimodal_content(text, imageUrl)
        → LangChain HumanMessage(content=[
            {"type": "text", "text": "..."},
            {"type": "image_url", "image_url": {"url": "data:..."}}
          ])
    → 视觉模型 (GPT-4o/GLM-4v/Qwen-VL/Gemini) 理解图片并回复
```
- `ImageUploader.tsx` — 缩略图预览 + 上传中 spinner + 错误状态；使用 `URL.createObjectURL()` 生成本地预览
- `ImageUploadService.java` — 服务端图片校验和存储
- `CleanupTask.java` — `@Scheduled(cron = "0 0 3 * * ?")` 每日凌晨 3 点清理 24h 前图片
- `multimodal.py` — **关键修复**：`image_path.lstrip("/")` 解决 Windows 下路径 `/uploads/...` 被 pathlib 当作根路径的问题

> **设计决策**：图片"先上传后引用"(upload-then-reference)，而非随消息一起发送 base64。好处：消息体轻量、图片可复用、避免大体积 JSON。

**语音输入 (STT without TTS)**：
```
[浏览器] 用户点击麦克风 → MediaRecorder API 录制 audio/webm
    → 停止录音 → POST /api/conversations/transcribe (multipart)
    → TranscribeController → 转发到 Python /speech/transcribe
    → OpenAI Whisper API (硬编码 api.openai.com/v1，DeepSeek 不支持)
    → 返回文字 → 填入输入框 → 用户确认后发送
```
- `AudioRecorder.tsx` — 脉冲动画 + 计时器 + 取消/发送按钮 + "转写中..." 状态
- `TranscribeController.java` — **关键修复**：使用 `ByteArrayResource` 重写 `getFilename()`，而非 `MultipartFile.getResource()`（后者返回 `InputStreamResource`，RestTemplate 序列化 multipart 时缺少 filename 字段导致失败）

**PDF 图片提取（双引擎）**：
- Python 端：`fitz.open()` → `page.get_images()` → `doc.extract_image()` 提取嵌入图片
- Java 端：`PDImageXObject` 从 PDF 页面资源中提取图片流
- 知识库入库时自动检测，图片存入 `image_paths` 列（分号分隔）

**Skill/Tool/Router 系统**：

Skill 把 AgentConfig（角色定义）+ ToolDef（工具定义）打包成一个可路由的能力单元，解决散落的三个实体如何协同的问题。

```
用户消息 "帮我查一下今天的热点新闻"
    │
    ▼
SkillRouter.route() (Java)
    │  1. 加载所有 Skill 的 name + description
    │  2. 调 Python /skills/route → embedding 匹配
    │  3. "联网搜索" Skill 得分 0.88 (vs "知识查询" 0.31)
    │
    ├── 命中 → 加载 Skill.agentConfig.systemPrompt + tools: [web_search]
    │         → StreamingProxy.streamSkillChat() → POST /chat/agent
    └── 未命中 → 走 MemoryPipeline 通用对话
        │
        ▼
[Python /chat/agent]
    skill_agent.skill_chat_stream(query, history, tools=["web_search"], system_prompt=...)
        │
        create_tool_calling_agent(llm, tools, prompt)
        │
        AgentExecutor.astream_events()
            ├── LLM 决定: tool_call web_search("今天热点新闻")
            │     → SSE event: {"event":"tool_call","tool":"web_search",...}
            ├── 实际执行 DuckDuckGo 搜索
            │     → SSE event: {"event":"tool_result","tool":"web_search",...}
            └── LLM 基于搜索结果生成最终回复
                  → SSE event: {"content":"今天的热点新闻有...",...}
```

**Tool/ToolDef/Skill 的关系**：
- `ToolDef` 是数据库表里的工具元数据（name, description, parameters），供 Java 侧 CRUD
- `tools/tool_registry.py` 是 Python 侧的运行时工具实例，把工具名映射到 LangChain `BaseTool` 对象
- `Skill` 通过 `orch_skill_tool` 关联表把多个 ToolDef 绑到自己的工具列表
- 路由匹配用的是 Skill 的 description 字段，跟话题检测用 embedding 做余弦相似度

**内置的 3 个免费工具**（零 API Key）：
- `web_search` — DuckDuckGo 搜索，返回网页摘要
- `wikipedia` — 维基百科查询，适用于概念解释
- `arxiv` — arXiv 学术论文搜索

> **设计决策**：MCP 暂未接入（`langchain-mcp-adapters` 与项目当前 `langchain-core 0.3.x` 不兼容，需要升级到 1.x），待后续版本兼容后再引入 MCP 生态的更多工具。

**文档上传处理流水线**：
```
上传文件 → PENDING → PARSING → CHUNKING → DONE
            ↓(失败)
          FAILED
```

核心设计——**策略模式**解析器：
- `DocumentParser` — 接口，定义 `supportedType()` 和 `parse()` 方法
- `PdfParser` — Apache PDFBox 解析 PDF
- `WordParser` — Apache POI 解析 .docx
- `MarkdownParser`、`TxtParser`、`HtmlParser` — 各自处理对应格式
- `ImageParser` — 图片文件不提取文本，标记 `needsVision=true`

分片器：
- `TextChunker` — 字符级分片（500 字符，50 字符重叠）
- `ImageChunker` — 单图单 chunk

关键服务：
- `DocIngestService.ingest()` — **理解完整的数据处理流水线**：
  1. 创文档记录（PENDING）
  2. 根据扩展名匹配解析器
  3. 解析文件 → 提取文本和图片信息
  4. 文本分片 → 创建 Chunk
  5. 图片标记 → 创建 image 类型 Chunk
  6. 更新状态为 DONE/FAILED
- `MultiModalCheck` — 检查模型是否支持视觉

实体：
- `Document` (`kb_document`) — 文档：fileName、fileType、status、chunkCount、tenantId
- `Chunk` (`kb_chunk`) — 分片：documentId、content、chunkType(text/image)、imagePath、vectorized

#### 3.4 编排模块 (agent-orchestration)
- `AgentConfig` (`orch_agent_config`) — Agent 配置：name、model、systemPrompt、temperature、maxTokens
- `PromptTemplate` (`orch_prompt_template`) — 提示词模板：name、template（含变量占位符）、variables（JSON 数组）
- `ToolDef` (`orch_tool_def`) — 工具定义：name、parameters（JSON Schema）、toolType(http/function/mcp)、endpoint

#### 3.5 工作流模块 (agent-workflow)
- `WorkflowDef` (`wf_workflow_def`) — 工作流：name、status(DRAFT/PUBLISHED/ARCHIVED)
- `WorkflowNode` (`wf_workflow_node`) — DAG 节点：nodeType(start/agent/tool/condition/end)、agentConfigId、toolDefId、position(JSON)、nodeConfig(JSON)
- `WorkflowEdge` (`wf_workflow_edge`) — DAG 边：sourceNodeId、targetNodeId、label、condition

关键方法：
- `WorkflowService.saveNodesAndEdges()` — **先删后建策略**（`delete(new LambdaQueryWrapper<>().eq(...))` 再 `insert()`）保证 DAG 一致性

#### 3.6 监控模块 (agent-monitor)
- `ApiLog` (`mon_api_log`) — API 调用日志：model、method、path、statusCode、responseTimeMs
- `TokenUsage` (`mon_token_usage`) — Token 用量：model、inputTokens、outputTokens、totalTokens、usageDate

#### 3.7 网关模块 (agent-gateway)
- `GatewayApplication` — Spring Boot 入口，`@MapperScan("com.agent.**.mapper")` 扫描所有 Mapper，聚合所有模块
- `CorsConfig` — 允许跨域
- `WebSocketConfig` — 注册 `/ws/chat` 端点
- `MqConfig` — RabbitMQ 队列/交换机绑定

---

### 第四阶段：Python AI 引擎（1-2 天）

**目标**：理解 AI 引擎如何接收请求、调用 LLM、返回流式结果。

#### 4.1 项目入口
- `main.py` — FastAPI 应用，定义所有 `/chat/stream`、`/chat/rag`、`/rag/retrieve` 等端点
- `config.py` — Pydantic Settings，从 `.env` 读取配置

#### 4.2 多模型路由
- `model_manager.py` — **核心设计：OpenAI 兼容 API 统一路由**
  ```python
  MODEL_ROUTES = {
      "deepseek-v4-pro": (base_url, api_key),
      "gpt-4o":         (base_url, api_key),
      "qwen-max":        (base_url, api_key),
      "glm-4":           (base_url, api_key),
      # 所有模型统一走 ChatOpenAI
  }
  ```
- `create_llm()` 根据模型名查找路由，返回 `ChatOpenAI` 实例
- 未匹配的模型自动 fallback 到 DeepSeek

#### 4.3 流式对话
- `chat_agent.py` — **理解 SSE 流式输出的实现**：
  ```python
  async def chat_stream(query, history, model, system_prompt, temperature):
      llm = create_llm(streaming=True)
      messages = format_messages(history) + [HumanMessage(content=query)]
      async for chunk in llm.astream(messages):  # 异步流式
          yield chunk.content
  ```
- FastAPI 通过 `StreamingResponse` 包装为 SSE 格式：`data: {"content": "...", "done": false}\n\n`

#### 4.4 RAG 检索增强生成
完整 RAG 流程：
```
1. 用户提问 → retrieve_context(query) → Pgvector 相似度检索
2. 检索到的文档片段 + 用户问题 → build_rag_prompt() → 构建增强提示词
3. 增强提示词 + LLM → 生成带引用的回答
```

关键文件：
- `vector_store.py` — Pgvector 集成，`add_documents()`、`similarity_search()`
- `retriever.py` — 检索 + 提示词构建

#### 4.5 LangGraph 工作流执行
- `workflow_executor.py` — 基于 LangGraph 的 DAG 执行器
- `WorkflowState` — 状态对象：input、messages、final_output
- `create_llm_node()` — 创建 LLM 调用节点
- `create_condition_node()` — 创建条件路由节点
- `execute_workflow()` — 从节点/边定义构建 StateGraph 并编译执行

#### 4.6 Celery 异步任务
- `celery_app.py` — Celery 配置（RabbitMQ 做 broker，Redis 做 backend）
- `doc_process.py` — 异步文档处理 + 图片描述
- `workflow_exec.py` — 异步工作流执行

---

### 第五阶段：React 前端（1-2 天）

**目标**：理解前端架构、状态管理、SSE 流式渲染。

#### 5.1 项目骨架
- `vite.config.ts` — Vite 配置，端口 3000，代理 `/api`→`9090`、`/ws`→`ws://9090`
- `tailwind.config.js` — 暗色主题 CSS 变量
- `App.tsx` — 路由守卫：无 token → 登录/注册页；有 token → 主布局（Sidebar + Header + 页面内容）

#### 5.2 状态管理
- `store/appStore.ts` — Zustand 全局状态：
  - 认证：`user`、`token`（持久化到 localStorage）、`setAuth()`、`logout()`
  - UI：`sidebarOpen`、`toggleSidebar()`
  - 业务：`currentTenantId`、`selectedModel`（默认 `deepseek-v4-pro`）

#### 5.3 API 客户端
- `api/client.ts` — 基于 Fetch 的 API 封装：
  - 自动注入 `Authorization: Bearer xxx` 头
  - 通用 `get/post/put/del` 方法
  - 特化方法：`login()`、`chatStream()`、`uploadDocument()`

#### 5.4 页面功能（8 个页面）
| 页面 | 核心功能 | 关键技术 |
|------|----------|----------|
| `Login.tsx` | 登录表单，成功后存 token 跳转 | Zustand setAuth |
| `Register.tsx` | 注册表单，注册即登录 | 同上 |
| `Dashboard.tsx` | 统计卡片 + 近期活动 + 系统状态 | 纯展示 |
| `Chat.tsx` | **流式对话**，SSE 逐字渲染 | `fetch` + `ReadableStream` + React 不可变更新 |
| `Knowledge.tsx` | 文件上传 + 文档列表 | FormData + multipart |
| `Workflow.tsx` | React Flow 拖拽 DAG 画布 | ReactFlow 库 |
| `Monitor.tsx` | Recharts 图表（Token 柱状图 + API 折线图） | Recharts |
| `Settings.tsx` | 模型选择 + 租户管理 CRUD + 登出 | 多组件组合 |

#### 5.5 重点：Chat 页面 SSE 流式渲染
```typescript
// 关键流程
const res = await fetch('/api/conversations/stream', { method: 'POST', body: json })
const reader = res.body.getReader()          // 获取 ReadableStream
while (true) {
    const { done, value } = await reader.read()
    // 解析 SSE 格式: data:{...}\n\n
    // 不可变更新最后一条 assistant 消息的 content
    setMessages((prev) => {
        updated[last] = { ...last, content: last.content + newContent }
        return updated
    })
}
```

> **调试经验**：React 18 StrictMode 会两次调用 state 更新函数来检测副作用。如果直接 `last.content += data` 突变了原对象，会导致内容被追加两次。必须用 `{ ...last, content: last.content + newContent }` 创建新对象。

---

### 第六阶段：端到端数据流（1 天）

**目标**：理解一条请求从头到尾经过的所有环节。

#### 6.1 用户登录流程
```
浏览器 → POST /api/auth/login
    → SecurityConfig: permitAll，放行
    → AuthController.login()
    → AuthService.login()
        → UserMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, ...))
        → PasswordEncoder.matches() 验证密码
        → JwtUtil.generateToken() 生成JWT
    → 返回 { token, user }
浏览器 → 存 token 到 localStorage + Zustand
    → 后续请求自动带 Authorization: Bearer xxx
```

#### 6.2 流式聊天完整链路（带会话记忆 + 多模态支持）
```
[浏览器 Chat.tsx]
    POST /api/conversations/{id}/stream
    Headers: Authorization: Bearer <JWT>, Content-Type: application/json
    Body: { message: "帮我查一下", modelName: "deepseek-v4-pro", imageUrl: "..." }
        │
        ▼
[Spring 后端 ConversationController.stream()]
    1. PiiMasker.mask() → "帮我查一下"
    2. TopicDetector.detect() → 关键词粗筛 → 同话题跳过 Embedding
    3. ChatService.saveUserMessage() → MessageMapper.insert() + Redis RPUSH
    4. MemoryPipeline.buildContext()
       └─ MessageCacheService.getHistory() → Redis LRANGE (24h TTL)
          └─ 缓存未命中 → MessageMapper.selectList() 回源 DB
       └─ TokenCounter.estimateTokens() → 中文/1.5
       └─ 超 4000 tokens? → CompressionService.buildCompressedContext()
          └─ 调 Python /memory/summarize → 旧消息压缩为摘要
    5. StreamingProxy.streamChat(maskedQuery, context, emitter, callback, imageUrl)
        │
        ▼
[StreamingProxy 新线程]
    HttpURLConnection POST → http://localhost:8000/chat/stream
    Body: {
        "query": "帮我查一下",
        "history": [{"role":"system","content":"摘要..."}, ...最近消息],
        "model": "deepseek-v4-pro",
        "imageUrl": "/uploads/chat-images/2024-01-01/xxx.png"
    }
        │
        ▼
[Python FastAPI /chat/stream]
    chat_stream(query, history, model, image_url=imageUrl)
        → 如果有 image_url 且模型支持vision:
            multimodal.encode_image_to_base64(image_url)
                → 从项目根目录 + agent-gateway/ 双路径查找
                → Base64 编码 → data:image/png;base64,...
            multimodal.build_multimodal_content(text, image_url)
                → LangChain HumanMessage(content=[text_block, image_block])
        → 非视觉模型: 普通 HumanMessage(content=text)
        → llm.astream(messages) → 逐token生成
        → yield f"data: {...}\n\n"
        │
        ▼ (SSE stream)
[DeepSeek/OpenAI/Gemini API] → 逐 token 返回
        │
        ▼
[StreamingProxy]
    BufferedReader.readLine() → 解析每行SSE
    收集 fullResponse += content → 流结束后 callback.onComplete(reply)
    → PiiMasker.mask(reply) → saveAssistantMessage(DB + Redis 双写)
    → MemoryPipeline.afterResponse() → LTRIM + 更新token统计 + 刷新TTL
    emitter.send(SseEmitter.event().data(jsonContent))
        │
        ▼ (SSE over HTTP)
[浏览器 Chat.tsx]
    reader.read() → TextDecoder.decode()
    解析 data:{...}\n\n → JSON.parse()
    setMessages({ ...last, content: last.content + token })
    实时渲染到页面（消息泡内图片可点击放大）
```

#### 6.3 文档上传 + 向量化流程
```
[浏览器 Knowledge.tsx]
    POST /api/knowledge/documents/upload (multipart)
    Headers: X-Tenant-Id: 1
        │
        ▼
[TenantInterceptor]
    提取 X-Tenant-Id → ThreadLocal.set(1)
        │
        ▼
[KnowledgeController]
    DocIngestService.ingest(file, tenantId)
        1. 创 Document(status=PENDING)
        2. findParser(ext) → 匹配解析器
        3. parser.parse() → { text, images, needsVision }
        4. TextChunker.chunk(text) → List<String>
        5. 每个chunk → Chunk(chunkType=text) → 入库
        6. 图片 → Chunk(chunkType=image, needsVision) → 入库
        7. Document(status=DONE, chunkCount=N)
    (后续可触发Celery异步向量化)
```

#### 6.4 工作流执行流程
```
[浏览器 Workflow.tsx]
    PUT /api/workflows/{id}/dag
    Body: { nodes: [...], edges: [...] }
        │
        ▼
[WorkflowController]
    WorkflowService.saveNodesAndEdges()
        1. 删除旧 nodes/edges
        2. 批量插入新 nodes/edges
        │
    (点击Run按钮)
    POST /workflow/execute (Python引擎)
        │
        ▼
[Python WorkflowExecutor]
    1. 根据 nodes/edges 构建 StateGraph
    2. start/agent 节点 → create_llm_node(model, systemPrompt)
    3. condition 节点 → create_condition_node(field, routing)
    4. end 节点 → 输出最终结果
    5. graph.compile().invoke(initialState)
```

---

## 关键技术概念速查

| 概念 | 项目中的体现 |
|------|-------------|
| **多租户隔离** | `TenantInterceptor` + `ThreadLocal` + 各表 `tenantId` 字段 |
| **JWT 无状态认证** | `JwtUtil` 生成/校验，`JwtFilter` 拦截，不存 Session |
| **SSE 流式传输** | Spring `SseEmitter` ↔ Python `StreamingResponse` ↔ 前端 `ReadableStream` |
| **策略模式** | `DocumentParser` 接口 + 7 个实现类（PDF/Word/MD/TXT/HTML/Image） |
| **RAG** | 文档→分块→向量化(Pgvector)→检索→增强提示词→LLM |
| **DAG 工作流** | React Flow(前端) → MyBatis-Plus 实体(后端) → LangGraph(Python) |
| **OpenAI 兼容 API** | 所有模型统一 `ChatOpenAI` 接口，模型名路由分配不同 base_url/api_key |
| **LangChain 多模态** | `HumanMessage(content=[text_block, image_block])` 传入 base64 图片给视觉模型 |
| **Tool Calling** | `create_tool_calling_agent` + `AgentExecutor`，LLM 自主决策调工具 → 执行 → 结果回喂 |
| **Skill = Agent + Tools** | Skill 表 + 关联表打包 AgentConfig + ToolDefs，语义 Router 自动匹配最佳技能 |
| **上传-引用模式** | 图片先上传到服务端获取 URL，消息体只传 URL 引用，避免大体积 JSON |
| **React 不可变状态** | `{ ...obj, field: newValue }` 而非 `obj.field = newValue` |
| **多模块 Maven** | 9 模块，`agent-gateway` 聚合启动，父 POM 统一版本管理 |
| **MyBatis-Plus** | `BaseMapper<T>` + `LambdaQueryWrapper` 类型安全查询，`@MapperScan` 扫描 |

---

## 动手实践建议

1. **跟着一条日志走**：在 Chat 页面发一条消息，观察 Java 日志输出（已开 DEBUG），从 `StreamingProxy` 日志追踪到 Python 引擎的输出，理解完整链路。

2. **加一个新模型**：在 `model_manager.py` 的 `MODEL_ROUTES` 加一个模型，前端 `MODELS` 数组也加对应项，体验多模型路由机制。

3. **加一个新解析器**：参照 `TxtParser` 写一个 CSV 文件解析器，实现 `DocumentParser` 接口，在 `DocIngestService` 中会自动发现（Spring 注入 `List<DocumentParser>`）。

4. **加一个新 API**：参照 `MonitorController` 的模式，在一个模块中新建 Entity → Mapper → Service → Controller，体验完整的分层开发。

5. **追踪租户隔离**：在 `TenantInterceptor` 打断点，观察每个请求的 `X-Tenant-Id` 如何传递和清理。

6. **体验 StrictMode**：注释掉 `main.tsx` 中的 `<React.StrictMode>`，观察 Chat 页面的流式渲染行为变化（理解 React 18 的副作用检测机制）。

7. **追踪多模态消息**：在 Chat 页面发一张图片，在 `multimodal.py` 的 `encode_image_to_base64()` 打断点，观察 Java → Python 的路径传递和 Base64 编码过程；再在 `chat_agent.py` 的 `build_multimodal_content()` 处观察 LangChain `HumanMessage` 的 `content` 列表结构。切换到非视觉模型（如 DeepSeek），观察优雅降级行为。

8. **追踪语音链路**：在 `AudioRecorder.tsx` 的 `recorder.onstop` 打断点，观察 Blob 创建 → `api.transcribeAudio()` → Java `TranscribeController` → Python `/speech/transcribe` → Whisper API 的完整链路。
