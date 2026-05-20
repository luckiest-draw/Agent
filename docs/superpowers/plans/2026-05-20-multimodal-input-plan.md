# 多模态输入：图片识别 + 语音转写 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Agent 平台聊天增加图片上传+视觉问答、语音转文字输入、PDF 内嵌图片提取

**Architecture:** 图片走"先上传再引用"模式（Java 存盘返回 URL），语音走流式转发（Java→Python→Whisper API），视觉模型在 Python 引擎构造多模态 LangChain 消息流式返回

**Tech Stack:** Java 17 / Spring Boot 3.2.5 / MyBatis-Plus / PDFBox / Python FastAPI / LangChain ChatOpenAI / OpenAI Whisper API / PyMuPDF / React 18 + TypeScript

---

## File Structure

**新建文件:**
```
agent-engine/models/multimodal.py              — 多模态消息构造 + 图片文件读取
agent-backend/agent-conversation/src/main/java/com/agent/conversation/service/ImageUploadService.java
agent-backend/agent-conversation/src/main/java/com/agent/conversation/controller/TranscribeController.java
agent-frontend/src/components/chat/ImageUploader.tsx
agent-frontend/src/components/chat/AudioRecorder.tsx
```

**修改文件:**
```
agent-engine/config.py                          — 加 Gemini vision_models + MODEL_ROUTES
agent-engine/main.py                            — /chat/stream 加 imageUrl, 新增 /speech/transcribe
agent-engine/agents/chat_agent.py               — format_messages 支持多模态
agent-engine/requirements.txt                   — 加 PyMuPDF
agent-backend/agent-conversation/src/main/java/com/agent/conversation/entity/Message.java
agent-backend/agent-conversation/src/main/java/com/agent/conversation/controller/ConversationController.java
agent-backend/agent-conversation/src/main/java/com/agent/conversation/service/StreamingProxy.java
agent-backend/agent-conversation/src/main/java/com/agent/conversation/service/ChatService.java
agent-backend/agent-conversation/src/main/java/com/agent/conversation/service/impl/ChatServiceImpl.java
agent-backend/agent-gateway/src/main/resources/application.yml
agent-backend/agent-gateway/src/main/resources/schema.sql
agent-backend/agent-gateway/src/main/java/com/agent/gateway/GatewayApplication.java
agent-backend/agent-knowledge/src/main/java/com/agent/knowledge/parser/PdfParser.java
agent-backend/agent-knowledge/src/main/java/com/agent/knowledge/service/impl/DocIngestServiceImpl.java
agent-frontend/src/pages/Chat.tsx
agent-frontend/src/api/client.ts
```

---

### Task 1: Python — 视觉模型配置 + Gemini 接入

**Files:**
- Modify: `agent-engine/config.py:42-49`
- Modify: `agent-engine/models/model_manager.py:5-19`

- [ ] **Step 1: 更新 config.py — 加 Gemini 配置 + 扩展 vision_models**

```python
# config.py — Settings 类追加
gemini_api_key: Optional[str] = None
gemini_base_url: str = "https://generativelanguage.googleapis.com/v1beta/openai"

# vision_models 列表替换为：
vision_models: list = [
    "gpt-4o", "gpt-4-turbo",
    "glm-4v",
    "qwen-vl-plus", "qwen-vl-max",
    "gemini-2.5-flash", "gemini-2.5-pro",
]
```

- [ ] **Step 2: 更新 model_manager.py — 加 Gemini 路由**

```python
MODEL_ROUTES = {
    "deepseek-chat": (settings.deepseek_base_url, settings.deepseek_api_key),
    "deepseek-v4-pro": (settings.deepseek_base_url, settings.deepseek_api_key),
    "deepseek-reasoner": (settings.deepseek_base_url, settings.deepseek_api_key),
    "gpt-4o": (settings.openai_base_url, settings.openai_api_key),
    "gpt-4-turbo": (settings.openai_base_url, settings.openai_api_key),
    "gpt-3.5-turbo": (settings.openai_base_url, settings.openai_api_key),
    "qwen-max": (settings.qwen_base_url, settings.qwen_api_key),
    "qwen-plus": (settings.qwen_base_url, settings.qwen_api_key),
    "qwen-vl-plus": (settings.qwen_base_url, settings.qwen_api_key),
    "qwen-vl-max": (settings.qwen_base_url, settings.qwen_api_key),
    "glm-4": (settings.glm_base_url, settings.glm_api_key),
    "glm-4v": (settings.glm_base_url, settings.glm_api_key),
    "gemini-2.5-flash": (settings.gemini_base_url, settings.gemini_api_key),
    "gemini-2.5-pro": (settings.gemini_base_url, settings.gemini_api_key),
}
```

- [ ] **Step 3: 提交**

```bash
cd D:/LearnJava/Agent && git add agent-engine/config.py agent-engine/models/model_manager.py && git commit -m "feat: add Gemini 2.5 Flash/Pro to vision models and MODEL_ROUTES"
```

---

### Task 2: Python — 多模态消息构造器

**Files:**
- Create: `agent-engine/models/multimodal.py`

- [ ] **Step 1: 创建 multimodal.py**

```python
# Multimodal utilities: read image file, build vision model messages
import base64
import logging
import os
import imghdr
from pathlib import Path
from typing import List, Dict, Optional

logger = logging.getLogger("multimodal")

IMAGE_EXT_TO_MIME = {
    "jpg": "image/jpeg", "jpeg": "image/jpeg",
    "png": "image/png",
    "gif": "image/gif",
    "webp": "image/webp",
    "bmp": "image/bmp",
}


def encode_image_to_base64(image_path: str) -> str:
    abs_path = Path(image_path)
    if not abs_path.is_absolute():
        # Relative to gateway uploads dir — resolve from project root
        abs_path = Path(__file__).parent.parent.parent.parent / "agent-gateway" / image_path
    if not abs_path.exists():
        abs_path = Path(image_path)
    if not abs_path.exists():
        raise FileNotFoundError(f"Image not found: {image_path} (tried {abs_path})")

    ext = abs_path.suffix.lstrip(".").lower()
    mime = IMAGE_EXT_TO_MIME.get(ext, "image/png")

    with open(abs_path, "rb") as f:
        data = base64.b64encode(f.read()).decode("utf-8")
    return f"data:{mime};base64,{data}"


def build_multimodal_content(text: str, image_url: str) -> list:
    data_uri = encode_image_to_base64(image_url)
    return [
        {"type": "text", "text": text or "请描述这张图片"},
        {"type": "image_url", "image_url": {"url": data_uri}},
    ]
```

- [ ] **Step 2: 提交**

```bash
cd D:/LearnJava/Agent && git add agent-engine/models/multimodal.py && git commit -m "feat: add multimodal message builder with base64 image encoding"
```

---

### Task 3: Python — chat_agent 支持多模态

**Files:**
- Modify: `agent-engine/agents/chat_agent.py:1-40`

- [ ] **Step 1: 改造 chat_stream 函数签名加 imageUrl 参数**

```python
# chat_agent.py — format_messages 替换为：
from typing import AsyncIterator, List, Dict, Optional
from langchain_core.messages import HumanMessage, SystemMessage, AIMessage
from models.model_manager import create_llm, supports_vision
from models.multimodal import build_multimodal_content
from rag.retriever import retrieve_context, build_rag_prompt
import logging

logger = logging.getLogger("chat_agent")


def format_messages(history: List[Dict], system_prompt: str = None) -> list:
    messages = []
    if system_prompt:
        messages.append(SystemMessage(content=system_prompt))
    for msg in history:
        role = msg.get("role")
        content = msg.get("content", "")
        if role == "user":
            messages.append(HumanMessage(content=content))
        elif role == "assistant":
            messages.append(AIMessage(content=content))
    return messages


async def chat_stream(
    query: str,
    history: List[Dict],
    model: str = None,
    system_prompt: str = None,
    temperature: float = 0.7,
    image_url: Optional[str] = None,
) -> AsyncIterator[str]:
    model_name = model or "deepseek-chat"

    if image_url and not supports_vision(model_name):
        yield f"[错误] 模型 {model_name} 不支持图片输入，请切换到 gpt-4o / glm-4v / qwen-vl / gemini"
        return

    llm = create_llm(model_name=model_name, temperature=temperature, streaming=True)
    messages = format_messages(history, system_prompt)

    if image_url:
        msg_content = build_multimodal_content(query, image_url)
        messages.append(HumanMessage(content=msg_content))
    else:
        messages.append(HumanMessage(content=query))

    async for chunk in llm.astream(messages):
        content = chunk.content if hasattr(chunk, "content") else str(chunk)
        if content:
            yield content


async def rag_chat_stream(
    query: str,
    history: List[Dict],
    model: str = None,
    system_prompt: str = None,
    temperature: float = 0.7,
    top_k: int = 5,
    image_url: Optional[str] = None,
) -> AsyncIterator[str]:
    model_name = model or "deepseek-chat"

    if image_url and not supports_vision(model_name):
        yield f"[错误] 模型 {model_name} 不支持图片输入，请切换到 gpt-4o / glm-4v / qwen-vl / gemini"
        return

    context_docs = retrieve_context(query, top_k)
    rag_system = build_rag_prompt(query, context_docs, system_prompt)

    llm = create_llm(model_name=model_name, temperature=temperature, streaming=True)
    messages = format_messages(history)
    messages.insert(0, SystemMessage(content=rag_system))

    if image_url:
        msg_content = build_multimodal_content(query, image_url)
        messages.append(HumanMessage(content=msg_content))
    else:
        messages.append(HumanMessage(content=query))

    async for chunk in llm.astream(messages):
        content = chunk.content if hasattr(chunk, "content") else str(chunk)
        if content:
            yield content
```

- [ ] **Step 2: 提交**

```bash
cd D:/LearnJava/Agent && git add agent-engine/agents/chat_agent.py && git commit -m "feat: add imageUrl support to chat_stream with vision model routing"
```

---

### Task 4: Python — /chat/stream 端点加 imageUrl + /speech/transcribe 端点

**Files:**
- Modify: `agent-engine/main.py:23-80`
- Modify: `agent-engine/requirements.txt`

- [ ] **Step 1: 更新 ChatRequest 加 imageUrl**

```python
# main.py — ChatRequest 类新增字段
class ChatRequest(BaseModel):
    query: str
    history: List[Dict] = []
    model: Optional[str] = None
    systemPrompt: Optional[str] = None
    temperature: float = 0.7
    maxTokens: int = 2048
    useRag: bool = False
    topK: int = 5
    imageUrl: Optional[str] = None   # 新增
```

- [ ] **Step 2: 修改 /chat/stream 端点传 imageUrl**

```python
@app.post("/chat/stream")
async def chat_stream_endpoint(request: ChatRequest):
    async def event_generator():
        try:
            async for content in chat_stream(
                query=request.query,
                history=request.history,
                model=request.model,
                system_prompt=request.systemPrompt,
                temperature=request.temperature,
                image_url=request.imageUrl,  # 新增
            ):
                yield f"data: {json.dumps({'content': content, 'done': False})}\n\n"
            yield f"data: {json.dumps({'content': '', 'done': True})}\n\n"
        except Exception as e:
            logger.error(f"Chat stream error: {e}")
            yield f"data: {json.dumps({'error': str(e), 'done': True})}\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "Connection": "keep-alive", "X-Accel-Buffering": "no"},
    )
```

- [ ] **Step 3: 同样修改 /chat/rag 端点传 imageUrl**

```python
@app.post("/chat/rag")
async def rag_chat_endpoint(request: ChatRequest):
    async def event_generator():
        try:
            async for content in rag_chat_stream(
                query=request.query,
                history=request.history,
                model=request.model,
                system_prompt=request.systemPrompt,
                temperature=request.temperature,
                top_k=request.topK,
                image_url=request.imageUrl,  # 新增
            ):
                yield f"data: {json.dumps({'content': content, 'done': False})}\n\n"
            yield f"data: {json.dumps({'content': '', 'done': True})}\n\n"
        except Exception as e:
            logger.error(f"RAG stream error: {e}")
            yield f"data: {json.dumps({'error': str(e), 'done': True})}\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "Connection": "keep-alive", "X-Accel-Buffering": "no"},
    )
```

- [ ] **Step 4: 新增 /speech/transcribe 端点 — 在 main.py 末尾、`if __name__` 之前插入**

```python
# ==================== Speech-to-Text ====================

from fastapi import UploadFile, File

@app.post("/speech/transcribe")
async def transcribe_audio(file: UploadFile = File(...)):
    """语音转文字,调用 OpenAI Whisper API"""
    from openai import OpenAI
    audio_bytes = await file.read()
    client = OpenAI(
        api_key=settings.openai_api_key or settings.deepseek_api_key,
        base_url=settings.openai_base_url,
    )
    try:
        result = client.audio.transcriptions.create(
            model="whisper-1",
            file=("audio.webm", audio_bytes, file.content_type or "audio/webm"),
            language="zh",
        )
        return {"text": result.text}
    except Exception as e:
        logger.error(f"Whisper transcription error: {e}")
        raise HTTPException(status_code=500, detail=f"语音转写失败: {str(e)}")
```

- [ ] **Step 5: requirements.txt 加 PyMuPDF**

```
# 在 requirements.txt 末尾追加:
PyMuPDF==1.25.1
```

- [ ] **Step 6: 提交**

```bash
cd D:/LearnJava/Agent && git add agent-engine/main.py agent-engine/requirements.txt && git commit -m "feat: add imageUrl to chat endpoints, add /speech/transcribe Whisper endpoint, add PyMuPDF"
```

---

### Task 5: Java — Message 实体 + schema.sql + ChatService 加 imageUrl

**Files:**
- Modify: `agent-backend/agent-conversation/src/main/java/com/agent/conversation/entity/Message.java`
- Modify: `agent-backend/agent-gateway/src/main/resources/schema.sql:105-114`
- Modify: `agent-backend/agent-conversation/src/main/java/com/agent/conversation/service/ChatService.java`
- Modify: `agent-backend/agent-conversation/src/main/java/com/agent/conversation/service/impl/ChatServiceImpl.java`

- [ ] **Step 1: Message.java 加 imageUrl 字段**

```java
package com.agent.conversation.entity;

import com.agent.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("conv_message")
public class Message extends BaseEntity {
    private Long conversationId;
    private String role;
    private String content;
    private String imageUrl;       // 新增
    private Integer tokenCount;
    private Long responseTimeMs;
}
```

- [ ] **Step 2: schema.sql conv_message 表加 image_url 列**

```sql
CREATE TABLE IF NOT EXISTS conv_message (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(10),
    content TEXT,
    image_url VARCHAR(500),         -- 新增
    token_count INTEGER,
    response_time_ms BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

- [ ] **Step 3: ChatService 接口加 imageUrl 参数**

```java
package com.agent.conversation.service;

import com.agent.conversation.entity.Message;

public interface ChatService {
    Message saveUserMessage(Long conversationId, String content);
    Message saveUserMessage(Long conversationId, String content, String imageUrl);
    Message saveAssistantMessage(Long conversationId, String content);
}
```

- [ ] **Step 4: ChatServiceImpl 实现带 imageUrl 的方法**

```java
// 新增方法
@Override
public Message saveUserMessage(Long conversationId, String content, String imageUrl) {
    Message msg = new Message();
    msg.setConversationId(conversationId);
    msg.setRole("user");
    msg.setContent(content);
    msg.setImageUrl(imageUrl);
    messageMapper.insert(msg);
    messageCacheService.addMessage(conversationId, "user", content);
    return msg;
}
```

- [ ] **Step 5: 提交**

```bash
cd D:/LearnJava/Agent && git add agent-backend/agent-conversation/src/main/java/com/agent/conversation/entity/Message.java agent-backend/agent-conversation/src/main/java/com/agent/conversation/service/ChatService.java agent-backend/agent-conversation/src/main/java/com/agent/conversation/service/impl/ChatServiceImpl.java agent-backend/agent-gateway/src/main/resources/schema.sql && git commit -m "feat: add imageUrl field to Message entity, schema, and ChatService"
```

---

### Task 6: Java — ImageUploadService

**Files:**
- Create: `agent-backend/agent-conversation/src/main/java/com/agent/conversation/service/ImageUploadService.java`

- [ ] **Step 1: 创建 ImageUploadService**

```java
package com.agent.conversation.service;

import com.agent.common.BusinessException;
import com.agent.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

@Service
public class ImageUploadService {

    private static final Logger log = LoggerFactory.getLogger(ImageUploadService.class);
    private static final Set<String> ALLOWED_TYPES = Set.of(
        "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp"
    );
    private static final long MAX_SIZE = 10 * 1024 * 1024; // 10MB
    private static final String UPLOAD_DIR = "uploads/chat-images";

    public record ImageUploadResult(String imageUrl, String fileName, long size) {}

    public ImageUploadResult upload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片大小不能超过 10MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_UNSUPPORTED,
                "不支持的图片格式: " + contentType + "，支持 JPG/PNG/GIF/WebP/BMP");
        }

        String ext = getExtension(contentType, file.getOriginalFilename());
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String fileName = UUID.randomUUID().toString() + "." + ext;

        try {
            Path uploadDir = Paths.get(UPLOAD_DIR, dateDir);
            Files.createDirectories(uploadDir);
            Path targetPath = uploadDir.resolve(fileName);
            file.transferTo(targetPath);

            String imageUrl = "/" + UPLOAD_DIR + "/" + dateDir + "/" + fileName;
            log.info("Image uploaded: {} ({} bytes)", imageUrl, file.getSize());
            return new ImageUploadResult(
                imageUrl.replace("\\", "/"),
                file.getOriginalFilename(),
                file.getSize()
            );
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "图片上传失败: " + e.getMessage());
        }
    }

    private String getExtension(String contentType, String fileName) {
        if (contentType.equals("image/jpeg")) return "jpg";
        if (contentType.equals("image/png")) return "png";
        if (contentType.equals("image/gif")) return "gif";
        if (contentType.equals("image/webp")) return "webp";
        if (contentType.equals("image/bmp")) return "bmp";
        // fallback from filename
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        }
        return "png";
    }
}
```

- [ ] **Step 2: 验证 ErrorCode 枚举**

确认 `agent-backend/agent-common/src/main/java/com/agent/common/ErrorCode.java` 已有 `BAD_REQUEST`、`FILE_TYPE_UNSUPPORTED`、`FILE_UPLOAD_FAILED`，无需新增。

- [ ] **Step 3: 提交**

```bash
cd D:/LearnJava/Agent && git add agent-backend/agent-conversation/src/main/java/com/agent/conversation/service/ImageUploadService.java && git commit -m "feat: add ImageUploadService with type/size validation"
```

---

### Task 7: Java — ConversationController 新增 upload-image 端点 + stream 端点加 imageUrl

**Files:**
- Modify: `agent-backend/agent-conversation/src/main/java/com/agent/conversation/controller/ConversationController.java`

- [ ] **Step 1: 在 ConversationController 注入 ImageUploadService，新增 upload-image 端点**

```java
// 新增注入
@Autowired
private ImageUploadService imageUploadService;

// 新增端点（在 @GetMapping("/{id}/messages") 之后插入）

@PostMapping("/upload-image")
public Result<Map<String, Object>> uploadImage(@RequestParam("file") MultipartFile file) {
    ImageUploadService.ImageUploadResult result = imageUploadService.upload(file);
    return Result.ok(Map.of(
        "imageUrl", result.imageUrl(),
        "fileName", result.fileName(),
        "size", result.size()
    ));
}
```

添加 import:
```java
import com.agent.conversation.service.ImageUploadService;
import org.springframework.web.multipart.MultipartFile;
```

- [ ] **Step 2: 修改 /api/conversations/stream 端点支持 imageUrl**

```java
/** 无会话ID的流式对话（前端 Chat 页新建对话） */
@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamChat(@RequestBody Map<String, Object> body) {
    SseEmitter emitter = new SseEmitter(300000L);
    String query = (String) body.get("query");
    String model = (String) body.getOrDefault("model", "deepseek-chat");
    String imageUrl = (String) body.getOrDefault("imageUrl", null);
    @SuppressWarnings("unchecked")
    List<Map<String, String>> history = (List<Map<String, String>>) body.getOrDefault("history", List.of());

    String maskedQuery = piiMasker.mask(query);

    Conversation conv = new Conversation();
    conv.setTitle(maskedQuery.length() > 30 ? maskedQuery.substring(0, 30) + "..." : maskedQuery);
    conv.setMessageCount(0);
    conversationMapper.insert(conv);

    chatService.saveUserMessage(conv.getId(), maskedQuery, imageUrl);
    MemoryContext context = memoryPipeline.buildContext(conv.getId(), maskedQuery);

    streamingProxy.streamChat(maskedQuery, context, conv.getId(), model, imageUrl, emitter,
        (fullResponse) -> {
            String maskedResponse = piiMasker.mask(fullResponse);
            chatService.saveAssistantMessage(conv.getId(), maskedResponse);
            memoryPipeline.afterResponse(conv.getId(), maskedQuery, maskedResponse);
        });
    return emitter;
}
```

- [ ] **Step 3: 修改 /api/conversations/{id}/stream 端点支持 imageUrl**

```java
/** 已有会话的流式对话（带话题检测 + 滑动窗口） */
@PostMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream(@PathVariable Long id, @RequestBody Map<String, String> body) {
    SseEmitter emitter = new SseEmitter(300000L);
    String rawMessage = body.get("message");
    String modelName = body.getOrDefault("modelName", null);
    String imageUrl = body.getOrDefault("imageUrl", null);

    // 1. PII 脱敏
    String maskedMessage = piiMasker.mask(rawMessage);

    // 2. 话题检测（可配置开关）
    Long activeConvId = id;
    if (topicDetectionEnabled) {
        TopicDetectionResult topicResult = topicDetector.detect(maskedMessage, id);
        if (topicResult.changed()) {
            Conversation sub = new Conversation();
            sub.setTitle(maskedMessage.length() > 30
                ? maskedMessage.substring(0, 30) + "..." : maskedMessage);
            sub.setParentId(id);
            conversationMapper.insert(sub);
            activeConvId = sub.getId();
        }
    }

    // 3. 保存用户消息
    chatService.saveUserMessage(activeConvId, maskedMessage, imageUrl);

    // 4. 构建上下文（Redis 滑动窗口 + Token 压缩）
    MemoryContext context = memoryPipeline.buildContext(activeConvId, maskedMessage);

    // 5. 发送流式请求，在回调中收集完整回复
    final Long finalConvId = activeConvId;
    streamingProxy.streamChat(maskedMessage, context, activeConvId, modelName, imageUrl, emitter,
        (fullResponse) -> {
            String maskedResponse = piiMasker.mask(fullResponse);
            chatService.saveAssistantMessage(finalConvId, maskedResponse);
            memoryPipeline.afterResponse(finalConvId, maskedMessage, maskedResponse);
        });
    return emitter;
}
```

- [ ] **Step 4: 提交**

```bash
cd D:/LearnJava/Agent && git add agent-backend/agent-conversation/src/main/java/com/agent/conversation/controller/ConversationController.java && git commit -m "feat: add upload-image endpoint and imageUrl support to stream endpoints"
```

---

### Task 8: Java — StreamingProxy 加 imageUrl 参数

**Files:**
- Modify: `agent-backend/agent-conversation/src/main/java/com/agent/conversation/service/StreamingProxy.java`

- [ ] **Step 1: 各 streamChat 重载加 imageUrl 参数，构造请求体时加入**

```java
// 替换 streamChat 方法签名，新增带 callback + imageUrl 版本
/** 带上下文 + 图片 + 回调 */
public void streamChat(String userMessage, MemoryContext context,
                       Long conversationId, String modelName, String imageUrl,
                       SseEmitter emitter, ResponseCallback callback) {
    new Thread(() -> {
        StringBuilder fullResponse = new StringBuilder();
        try {
            URI uri = URI.create(engineUrl + "/chat/stream");
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(120000);

            List<Map<String, String>> history = new ArrayList<>();
            if (context.summary() != null && !context.summary().isEmpty()) {
                history.add(Map.of("role", "system", "content", context.summary()));
            }
            for (MessageInfo msg : context.messages()) {
                history.add(Map.of("role", msg.role(), "content", msg.content()));
            }

            Map<String, Object> body = new HashMap<>();
            body.put("query", userMessage);
            body.put("history", history);
            body.put("model", modelName != null ? modelName : "deepseek-chat");
            if (imageUrl != null && !imageUrl.isEmpty()) {
                body.put("imageUrl", imageUrl);
            }

            String json = mapper.writeValueAsString(body);
            log.info("StreamingProxy -> {} msgs={} tokens={} image={}", uri, history.size(),
                context.estimatedTokens(), imageUrl != null);

            // ... (rest of method unchanged: write body, read SSE stream, etc.)
```

完整方法（从现有代码搬运剩余部分）：

```java
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int status = conn.getResponseCode();
            if (status != 200) {
                log.error("Python engine returned status {}", status);
                emitter.completeWithError(new RuntimeException("Engine error: HTTP " + status));
                return;
            }

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data:")) {
                    String data = line.substring(5).trim();
                    try {
                        var event = mapper.readValue(data, Map.class);
                        if (event.containsKey("content")) {
                            String content = (String) event.get("content");
                            fullResponse.append(content);
                        }
                    } catch (Exception ignored) {}
                    emitter.send(SseEmitter.event().data(data));
                }
            }
            reader.close();
            emitter.complete();
            if (callback != null) {
                callback.onComplete(fullResponse.toString());
            }
            log.info("StreamingProxy completed for conv {}, response {} chars",
                conversationId, fullResponse.length());
        } catch (Exception e) {
            log.error("StreamingProxy error for conv {}: {}", conversationId, e.getMessage());
            emitter.completeWithError(e);
            if (callback != null) callback.onError(e);
        }
    }).start();
}
```

- [ ] **Step 2: 更新无回调版本签名**

```java
/** 无回调版本 */
public void streamChat(String userMessage, MemoryContext context,
                       Long conversationId, String modelName, String imageUrl,
                       SseEmitter emitter) {
    streamChat(userMessage, context, conversationId, modelName, imageUrl, emitter, null);
}
```

- [ ] **Step 3: 更新兼容旧签名的版本**

```java
/** 兼容旧签名 */
public void streamChat(String userMessage, List<Map<String, String>> history,
                       Long conversationId, String modelName, SseEmitter emitter) {
    streamChat(userMessage,
        new MemoryContext(null,
            history != null ? history.stream()
                .map(m -> new MessageInfo(
                    m.getOrDefault("role", "user"),
                    m.getOrDefault("content", "")))
                .toList()
            : List.of(),
            0),
        conversationId, modelName, null, emitter, null);
}
```

- [ ] **Step 4: 提交**

```bash
cd D:/LearnJava/Agent && git add agent-backend/agent-conversation/src/main/java/com/agent/conversation/service/StreamingProxy.java && git commit -m "feat: add imageUrl parameter to StreamingProxy"
```

---

### Task 9: Java — TranscribeController

**Files:**
- Create: `agent-backend/agent-conversation/src/main/java/com/agent/conversation/controller/TranscribeController.java`

- [ ] **Step 1: 创建 TranscribeController**

```java
package com.agent.conversation.controller;

import com.agent.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
public class TranscribeController {

    private static final Logger log = LoggerFactory.getLogger(TranscribeController.class);

    @Value("${ai.engine.url:http://localhost:8000}")
    private String engineUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/transcribe")
    public Result<Map<String, String>> transcribe(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return Result.fail("音频文件为空");
        }

        String url = engineUrl + "/speech/transcribe";

        // 用 RestTemplate 转发 multipart 到 Python 引擎
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file.getResource());

        var requestEntity = new HttpEntity<>(body, headers);
        try {
            var response = restTemplate.postForEntity(url, requestEntity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String text = (String) response.getBody().get("text");
                return Result.ok(Map.of("text", text));
            }
            return Result.fail("语音转写失败");
        } catch (Exception e) {
            log.error("Transcribe error: {}", e.getMessage());
            return Result.fail("语音转写服务异常: " + e.getMessage());
        }
    }
}
```

- [ ] **Step 2: 提交**

```bash
cd D:/LearnJava/Agent && git add agent-backend/agent-conversation/src/main/java/com/agent/conversation/controller/TranscribeController.java && git commit -m "feat: add TranscribeController forwarding audio to Python Whisper endpoint"
```

---

### Task 10: Java — 静态资源配置 + 定时清理

**Files:**
- Modify: `agent-backend/agent-gateway/src/main/resources/application.yml`
- Modify: `agent-backend/agent-gateway/src/main/java/com/agent/gateway/GatewayApplication.java`

- [ ] **Step 1: application.yml 加静态资源配置**

在 `spring:` 段下追加：

```yaml
  web:
    resources:
      static-locations: file:uploads/
```

- [ ] **Step 2: GatewayApplication 加 @EnableScheduling**

```java
package com.agent.gateway;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.agent")
@MapperScan("com.agent.**.mapper")
@EnableAsync
@EnableScheduling
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
```

- [ ] **Step 3: 创建定时清理组件**

新建文件 `agent-backend/agent-gateway/src/main/java/com/agent/gateway/CleanupTask.java`:

```java
package com.agent.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class CleanupTask {

    private static final Logger log = LoggerFactory.getLogger(CleanupTask.class);
    private static final long MAX_AGE_HOURS = 24;

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupOldChatImages() {
        Path dir = Paths.get("uploads/chat-images");
        if (!Files.exists(dir)) return;

        Instant cutoff = Instant.now().minus(MAX_AGE_HOURS, ChronoUnit.HOURS);
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.lastModifiedTime().toInstant().isBefore(cutoff)) {
                        try {
                            Files.delete(file);
                        } catch (IOException e) {
                            log.warn("Failed to delete old image: {}", file);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    try {
                        if (Files.list(dir).findAny().isEmpty()) {
                            Files.delete(dir);
                        }
                    } catch (IOException ignored) {}
                    return FileVisitResult.CONTINUE;
                }
            });
            log.info("Chat image cleanup completed");
        } catch (IOException e) {
            log.error("Chat image cleanup error: {}", e.getMessage());
        }
    }
}
```

- [ ] **Step 4: 提交**

```bash
cd D:/LearnJava/Agent && git add agent-backend/agent-gateway/src/main/resources/application.yml agent-backend/agent-gateway/src/main/java/com/agent/gateway/GatewayApplication.java agent-backend/agent-gateway/src/main/java/com/agent/gateway/CleanupTask.java && git commit -m "feat: add static resource mapping and scheduled cleanup for chat images"
```

---

### Task 11: Java — PdfParser 提取 PDF 内嵌图片

**Files:**
- Modify: `agent-backend/agent-knowledge/src/main/java/com/agent/knowledge/parser/PdfParser.java`

- [ ] **Step 1: 重写 PdfParser 支持图片提取**

```java
package com.agent.knowledge.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Component
public class PdfParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(PdfParser.class);

    @Override public String supportedType() { return "pdf"; }

    @Override
    public Map<String, String> parse(InputStream inputStream, String fileName) throws Exception {
        Map<String, String> result = new HashMap<>();
        byte[] bytes = inputStream.readAllBytes();

        try (PDDocument doc = Loader.loadPDF(bytes)) {
            // 1. 提取文本
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            result.put("text", text != null ? text.trim() : "");

            // 2. 提取内嵌图片
            List<String> imagePaths = new ArrayList<>();
            Path uploadDir = Paths.get("uploads", "images");
            Files.createDirectories(uploadDir);

            int imageIndex = 0;
            for (PDPage page : doc.getPages()) {
                PDResources resources = page.getResources();
                if (resources == null) continue;
                for (COSName name : resources.getXObjectNames()) {
                    if (!resources.isImageXObject(name)) continue;
                    try {
                        PDImageXObject image = (PDImageXObject) resources.getXObject(name);
                        BufferedImage bufferedImage = image.getImage();
                        String imgName = UUID.randomUUID().toString() + ".png";
                        Path imgPath = uploadDir.resolve(imgName);
                        ImageIO.write(bufferedImage, "png", imgPath.toFile());
                        imagePaths.add(imgPath.toString().replace("\\", "/"));
                        imageIndex++;
                    } catch (Exception e) {
                        log.warn("Failed to extract image {} from PDF: {}", imageIndex, e.getMessage());
                    }
                }
            }

            if (!imagePaths.isEmpty()) {
                result.put("images", String.join(";", imagePaths));
                result.put("needsVision", "true");
                log.info("Extracted {} images from PDF: {}", imagePaths.size(), fileName);
            }
        }
        return result;
    }
}
```

- [ ] **Step 2: 提交**

```bash
cd D:/LearnJava/Agent && git add agent-backend/agent-knowledge/src/main/java/com/agent/knowledge/parser/PdfParser.java && git commit -m "feat: extract embedded images from PDF using PDFBox PDImageXObject"
```

---

### Task 12: Java — DocIngestServiceImpl 支持多图片

**Files:**
- Modify: `agent-backend/agent-knowledge/src/main/java/com/agent/knowledge/service/impl/DocIngestServiceImpl.java:89-98`

- [ ] **Step 1: images 字段可能包含多个路径（分号分隔），每种路径创建一个 chunk**

```java
// 替换原有的单图片处理逻辑 (约第89-98行)
if (images != null && !images.isBlank()) {
    String[] imagePathArray = images.split(";");
    for (String imgPath : imagePathArray) {
        if (imgPath.isBlank()) continue;
        Chunk chunk = new Chunk();
        chunk.setDocumentId(doc.getId());
        chunk.setContent(needsVision ? "[需要多模态处理]" : "");
        chunk.setChunkType("image");
        chunk.setImagePath(imgPath.trim());
        chunk.setChunkIndex(chunkIndex);
        chunkMapper.insert(chunk);
        chunkIndex++;
    }
}
```

- [ ] **Step 2: 提交**

```bash
cd D:/LearnJava/Agent && git add agent-backend/agent-knowledge/src/main/java/com/agent/knowledge/service/impl/DocIngestServiceImpl.java && git commit -m "feat: support multiple images per document in DocIngestService"
```

---

### Task 13: 前端 — API client 新增 uploadImage 和 transcribeAudio

**Files:**
- Modify: `agent-frontend/src/api/client.ts`

- [ ] **Step 1: client.ts 新增两个方法**

```typescript
// 在 api 对象中新增:

// Image upload (multipart)
uploadImage: (file: File, tenantId?: number) => {
  const formData = new FormData();
  formData.append('file', file);
  const h: Record<string, string> = {};
  if (authToken) h['Authorization'] = `Bearer ${authToken}`;
  return fetch(`${BASE_URL}/conversations/upload-image`, {
    method: 'POST',
    headers: h,
    body: formData,
  }).then(res => res.json());
},

// Audio transcription (multipart)
transcribeAudio: (audioBlob: Blob) => {
  const formData = new FormData();
  formData.append('file', audioBlob, 'recording.webm');
  const h: Record<string, string> = {};
  if (authToken) h['Authorization'] = `Bearer ${authToken}`;
  return fetch(`${BASE_URL}/conversations/transcribe`, {
    method: 'POST',
    headers: h,
    body: formData,
  }).then(res => res.json());
},
```

- [ ] **Step 2: 提交**

```bash
cd D:/LearnJava/Agent && git add agent-frontend/src/api/client.ts && git commit -m "feat: add uploadImage and transcribeAudio to frontend API client"
```

---

### Task 14: 前端 — ImageUploader 组件

**Files:**
- Create: `agent-frontend/src/components/chat/ImageUploader.tsx`

- [ ] **Step 1: 创建 ImageUploader 组件**

```tsx
import { useRef, useState } from 'react';
import { ImagePlus, X } from 'lucide-react';
import { api } from '@/api/client';

interface ImageInfo {
  id: string;
  file: File;
  localUrl: string;
  serverUrl: string | null;
  uploading: boolean;
  error: boolean;
}

interface Props {
  images: ImageInfo[];
  onChange: (images: ImageInfo[]) => void;
}

export default function ImageUploader({ images, onChange }: Props) {
  const inputRef = useRef<HTMLInputElement>(null);

  const handleSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files) return;

    const newImages: ImageInfo[] = [];
    for (const file of files) {
      newImages.push({
        id: crypto.randomUUID(),
        file,
        localUrl: URL.createObjectURL(file),
        serverUrl: null,
        uploading: true,
        error: false,
      });
    }

    const updated = [...images, ...newImages];
    onChange(updated);

    // Backend upload
    for (const img of newImages) {
      api.uploadImage(img.file)
        .then(res => {
          if (res.code === 200) {
            onChange((prev: ImageInfo[]) =>
              prev.map(p =>
                p.id === img.id ? { ...p, serverUrl: res.data.imageUrl, uploading: false } : p
              )
            );
          } else {
            onChange((prev: ImageInfo[]) =>
              prev.map(p => (p.id === img.id ? { ...p, uploading: false, error: true } : p))
            );
          }
        })
        .catch(() => {
          onChange((prev: ImageInfo[]) =>
            prev.map(p => (p.id === img.id ? { ...p, uploading: false, error: true } : p))
          );
        });
    }

    if (inputRef.current) inputRef.current.value = '';
  };

  const removeImage = (id: string) => {
    const img = images.find(i => i.id === id);
    if (img?.localUrl) URL.revokeObjectURL(img.localUrl);
    onChange(images.filter(i => i.id !== id));
  };

  return (
    <div className="flex gap-2 flex-wrap mb-2">
      {images.map(img => (
        <div key={img.id} className="relative w-20 h-20 rounded-lg overflow-hidden border border-border shrink-0">
          <img
            src={img.localUrl}
            alt="Preview"
            className={`w-full h-full object-cover ${img.uploading ? 'opacity-50' : ''} ${img.error ? 'border-2 border-red-500' : ''}`}
          />
          {img.uploading && (
            <div className="absolute inset-0 flex items-center justify-center bg-black/20">
              <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
            </div>
          )}
          {img.error && (
            <div className="absolute inset-0 flex items-center justify-center bg-red-500/20 text-red-500 text-xs">
              !
            </div>
          )}
          <button
            onClick={() => removeImage(img.id)}
            className="absolute top-0.5 right-0.5 w-5 h-5 bg-black/50 rounded-full flex items-center justify-center hover:bg-black/70"
          >
            <X className="w-3 h-3 text-white" />
          </button>
        </div>
      ))}
      <button
        onClick={() => inputRef.current?.click()}
        className="w-20 h-20 rounded-lg border-2 border-dashed border-border flex items-center justify-center hover:border-primary/50 transition-colors shrink-0"
      >
        <ImagePlus className="w-5 h-5 text-muted-foreground" />
      </button>
      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        multiple
        onChange={handleSelect}
        className="hidden"
      />
    </div>
  );
}

export type { ImageInfo };
```

- [ ] **Step 2: 提交**

```bash
cd D:/LearnJava/Agent && git add agent-frontend/src/components/chat/ImageUploader.tsx && git commit -m "feat: add ImageUploader component with thumbnail preview and upload progress"
```

---

### Task 15: 前端 — AudioRecorder 组件

**Files:**
- Create: `agent-frontend/src/components/chat/AudioRecorder.tsx`

- [ ] **Step 1: 创建 AudioRecorder 组件**

```tsx
import { useState, useRef, useCallback } from 'react';
import { Mic, X, Send } from 'lucide-react';
import { api } from '@/api/client';

interface Props {
  onTranscribed: (text: string) => void;
  disabled?: boolean;
}

export default function AudioRecorder({ onTranscribed, disabled }: Props) {
  const [recording, setRecording] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const [transcribing, setTranscribing] = useState(false);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const timerRef = useRef<number>(0);

  const startRecording = useCallback(async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const recorder = new MediaRecorder(stream, { mimeType: 'audio/webm' });
      mediaRecorderRef.current = recorder;
      chunksRef.current = [];

      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) chunksRef.current.push(e.data);
      };

      recorder.onstop = async () => {
        stream.getTracks().forEach(t => t.stop());
        if (chunksRef.current.length === 0) return;
        setTranscribing(true);
        const blob = new Blob(chunksRef.current, { type: 'audio/webm' });
        try {
          const res = await api.transcribeAudio(blob);
          if (res.code === 200 && res.data?.text) {
            onTranscribed(res.data.text);
          }
        } catch (err) {
          console.error('Transcribe error:', err);
        } finally {
          setTranscribing(false);
          setRecording(false);
          setElapsed(0);
        }
      };

      recorder.start();
      setRecording(true);
      setElapsed(0);
      timerRef.current = window.setInterval(() => {
        setElapsed(prev => prev + 1);
      }, 1000);
    } catch (err) {
      console.error('Microphone error:', err);
    }
  }, [onTranscribed]);

  const stopAndSend = () => {
    clearInterval(timerRef.current);
    mediaRecorderRef.current?.stop();
  };

  const cancelRecording = () => {
    clearInterval(timerRef.current);
    if (mediaRecorderRef.current?.state === 'recording') {
      mediaRecorderRef.current.onstop = () => {};
      mediaRecorderRef.current.stream.getTracks().forEach(t => t.stop());
    }
    chunksRef.current = [];
    setRecording(false);
    setElapsed(0);
  };

  const formatTime = (s: number) => {
    const m = Math.floor(s / 60);
    const sec = s % 60;
    return `${m}:${sec.toString().padStart(2, '0')}`;
  };

  if (transcribing) {
    return (
      <div className="flex items-center gap-2 px-3 py-2 text-sm text-muted-foreground">
        <div className="w-3 h-3 border-2 border-primary border-t-transparent rounded-full animate-spin" />
        转写中...
      </div>
    );
  }

  if (recording) {
    return (
      <div className="flex items-center gap-3 px-3 py-2 bg-red-50 dark:bg-red-950/20 rounded-lg">
        <div className="w-2.5 h-2.5 rounded-full bg-red-500 animate-pulse" />
        <span className="text-sm text-red-600 dark:text-red-400 font-mono">
          {formatTime(elapsed)}
        </span>
        <div className="flex-1" />
        <button
          onClick={cancelRecording}
          className="px-2 py-1 text-xs rounded hover:bg-red-100 dark:hover:bg-red-900/30"
        >
          取消
        </button>
        <button
          onClick={stopAndSend}
          className="flex items-center gap-1 px-3 py-1 text-xs rounded bg-primary text-primary-foreground hover:opacity-90"
        >
          <Send className="w-3 h-3" />
          发送
        </button>
      </div>
    );
  }

  return (
    <button
      onClick={startRecording}
      disabled={disabled}
      className="p-2 rounded-lg hover:bg-accent disabled:opacity-50 transition-colors"
      title="录音"
    >
      <Mic className="w-5 h-5 text-muted-foreground" />
    </button>
  );
}
```

- [ ] **Step 2: 提交**

```bash
cd D:/LearnJava/Agent && git add agent-frontend/src/components/chat/AudioRecorder.tsx && git commit -m "feat: add AudioRecorder component with MediaRecorder + Whisper transcription"
```

---

### Task 16: 前端 — Chat.tsx 集成图片+语音+消息气泡展示

**Files:**
- Modify: `agent-frontend/src/pages/Chat.tsx`

- [ ] **Step 1: 重写 Chat.tsx**

```tsx
import { useState, useRef, useEffect } from 'react'
import { useAppStore } from '@/store/appStore'
import { Send, Bot, User } from 'lucide-react'
import ImageUploader, { type ImageInfo } from '@/components/chat/ImageUploader'
import AudioRecorder from '@/components/chat/AudioRecorder'

interface Message {
  role: 'user' | 'assistant'
  content: string
  imageUrl?: string
}

export default function Chat() {
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [streaming, setStreaming] = useState(false)
  const [images, setImages] = useState<ImageInfo[]>([])
  const [showImageViewer, setShowImageViewer] = useState<string | null>(null)
  const selectedModel = useAppStore((s) => s.selectedModel)
  const scrollRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    scrollRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const handleSend = async () => {
    if (streaming) return
    const text = input.trim()
    if (!text && images.length === 0) return
    setStreaming(true)

    // Collect ready server URLs
    const readyImages = images.filter(img => img.serverUrl && !img.uploading)
    const stillUploading = images.filter(img => !img.serverUrl || img.uploading)

    if (stillUploading.length > 0) {
      // Some images still uploading — could wait, but for now skip them
      console.warn('Some images not yet uploaded, skipping')
    }

    const primaryImageUrl = readyImages.length > 0 ? readyImages[0].serverUrl : undefined

    const userMsg: Message = {
      role: 'user',
      content: text || '请描述这张图片',
      imageUrl: primaryImageUrl || undefined,
    }
    setMessages((prev) => [...prev, userMsg])
    setInput('')
    setImages([])
    setStreaming(true)

    try {
      const token = useAppStore.getState().token
      const res = await fetch('/api/conversations/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({
          query: text || '请描述这张图片',
          history: messages,
          model: selectedModel,
          imageUrl: primaryImageUrl || undefined,
        }),
      })
      const reader = res.body?.getReader()
      if (!reader) return

      setMessages((prev) => [...prev, { role: 'assistant', content: '' }])

      const decoder = new TextDecoder()
      let buffer = ''
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''
        for (const line of lines) {
          if (line.startsWith('data:')) {
            try {
              const jsonStr = line.slice(5).trim();
              if (!jsonStr) continue;
              const data = JSON.parse(jsonStr);
              if (data.done) break
              setMessages((prev) => {
                const updated = [...prev]
                const last = updated[updated.length - 1]
                if (last.role === 'assistant') {
                  updated[updated.length - 1] = { ...last, content: last.content + (data.content || '') }
                }
                return updated
              })
            } catch {}
          }
        }
      }
    } catch (err) {
      console.error('Stream error:', err)
    } finally {
      setStreaming(false)
    }
  }

  const handleTranscribed = (text: string) => {
    setInput(prev => prev ? prev + ' ' + text : text)
  }

  return (
    <div className="flex flex-col h-[calc(100vh-7rem)] max-w-3xl mx-auto">
      <div className="mb-4">
        <h1 className="text-2xl font-bold">Chat</h1>
        <p className="text-muted-foreground text-sm">Model: {selectedModel}</p>
      </div>

      <div className="flex-1 overflow-auto space-y-4 pr-2">
        {messages.length === 0 && (
          <div className="flex flex-col items-center justify-center h-full text-muted-foreground">
            <Bot className="w-12 h-12 mb-4 opacity-20" />
            <p>开始对话，可以上传图片或发送语音</p>
          </div>
        )}
        {messages.map((msg, i) => (
          <div key={i} className={`flex gap-3 ${msg.role === 'user' ? 'justify-end' : ''}`}>
            {msg.role === 'assistant' && (
              <div className="w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center shrink-0">
                <Bot className="w-4 h-4 text-primary" />
              </div>
            )}
            <div className={`max-w-[80%] rounded-xl px-4 py-3 text-sm ${
              msg.role === 'user'
                ? 'bg-primary text-primary-foreground'
                : 'bg-card border border-border'
            }`}>
              {msg.imageUrl && (
                <img
                  src={msg.imageUrl}
                  alt="Attached"
                  className="max-w-[300px] max-h-[200px] rounded-lg mb-2 cursor-pointer hover:opacity-90"
                  onClick={() => setShowImageViewer(msg.imageUrl!)}
                />
              )}
              {msg.content && <p className="whitespace-pre-wrap">{msg.content}</p>}
            </div>
            {msg.role === 'user' && (
              <div className="w-8 h-8 rounded-lg bg-accent flex items-center justify-center shrink-0">
                <User className="w-4 h-4" />
              </div>
            )}
          </div>
        ))}
        <div ref={scrollRef} />
      </div>

      <div className="mt-4 space-y-2">
        <ImageUploader images={images} onChange={setImages} />
        <div className="flex gap-2">
          <AudioRecorder onTranscribed={handleTranscribed} disabled={streaming} />
          <input
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSend()}
            placeholder="输入消息..."
            disabled={streaming}
            className="flex-1 px-4 py-3 rounded-xl border border-border bg-card text-sm focus:outline-none focus:ring-2 focus:ring-primary/20 disabled:opacity-50"
          />
          <button
            onClick={handleSend}
            disabled={streaming || (!input.trim() && images.length === 0)}
            className="px-4 py-3 rounded-xl bg-primary text-primary-foreground hover:opacity-90 disabled:opacity-50 transition-opacity"
          >
            <Send className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Image lightbox */}
      {showImageViewer && (
        <div
          className="fixed inset-0 bg-black/80 z-50 flex items-center justify-center cursor-pointer"
          onClick={() => setShowImageViewer(null)}
        >
          <img
            src={showImageViewer}
            alt="Viewer"
            className="max-w-[90vw] max-h-[90vh] rounded-lg object-contain"
          />
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 2: 提交**

```bash
cd D:/LearnJava/Agent && git add agent-frontend/src/pages/Chat.tsx && git commit -m "feat: integrate image upload, audio recording, and image lightbox into Chat"
```

---

### Task 17: 编译验证

- [ ] **Step 1: 编译 Java 后端**

```bash
cd D:/LearnJava/Agent/agent-backend && mvn clean compile
```

Expected: `BUILD SUCCESS` 所有模块通过。

- [ ] **Step 2: 编译前端**

```bash
cd D:/LearnJava/Agent/agent-frontend && npx tsc --noEmit
```

Expected: 无类型错误。

- [ ] **Step 3: 提交（如有修复）**

---

## 自检

1. **Spec 覆盖**: 所有 spec 需求均有对应 Task — 图片上传(T6,T7)、视觉问答(T2-T4,T8)、语音转写(T4,T9,T15)、PDF图片提取(T11-T12)、静态资源+清理(T10)、前端缩略图预览(T14)、录音状态栏(T15)、消息气泡图片(T16)
2. **无占位符**: 所有步骤包含完整代码
3. **类型一致性**: ImageInfo 类型在 ImageUploader.tsx 定义并导出，Chat.tsx 中引用一致；MemoryContext 来自 MemoryPipeline，所有签名一致；ChatService 接口与实现参数一致
