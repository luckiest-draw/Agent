# 多模态输入：图片识别 + 语音转写

## 概述

为 Agent 平台的聊天功能增加两种输入模态：
- **图片上传 + 视觉问答**：用户在对话中上传图片，视觉模型（GPT-4o / GLM-4v / Qwen-VL / Gemini）理解图片内容并回答相关问题
- **语音转文字**：用户录音 → OpenAI Whisper API 转写 → 文本进入聊天流程，模型以文字回复
- **PDF 内嵌图片提取**：扩展现有 PDF 解析器，提取 PDF 中内嵌图片送入视觉描述管道

## 架构

```
聊天输入
├── 📎 图片按钮 → 上传 → 返回 URL → 附在消息里发给视觉模型
└── 🎤 录音按钮 → 录音 → 转写 → 文本填入输入框 → 用户发送

新增调用链：

图片:  Frontend → POST /api/conversations/upload-image → Java 存盘到 uploads/chat-images/
        → POST /api/conversations/{id}/stream {message, imageUrl} → Java → Python /chat/stream
        → Python 读图片文件 → base64 → 视觉模型多模态消息 → SSE 流式返回

语音:  Frontend 录音(WebM) → POST /api/conversations/transcribe → Java
        → Python /speech/transcribe → OpenAI Whisper API → 返回文本
        → 前端填入输入框，用户可编辑后手动发送
```

## 接口设计

### POST /api/conversations/upload-image（新增）

```
Content-Type: multipart/form-data
  file: (binary image, max 10MB, JPG/PNG/GIF/WebP)

Response: { "code": 200, "data": { "imageUrl": "/uploads/chat-images/2026-05-20/uuid.png", "fileName": "...", "size": 245760 } }
```

- 文件存到 `agent-gateway/uploads/chat-images/{date}/{uuid}.{ext}`
- Spring 静态资源映射暴露 `/uploads/**`
- 校验类型和大小，非法文件拒绝

### POST /api/conversations/transcribe（新增）

```
Content-Type: multipart/form-data
  file: (audio/webm, max 30s)

Response: { "code": 200, "data": { "text": "转写结果文本" } }
```

- 音频不落盘，Java 直接流转发到 Python 引擎
- Python 引擎调 `POST https://api.openai.com/v1/audio/transcriptions` (Whisper-1)

### POST /api/conversations/{id}/stream（改造）

请求新增可选字段 `imageUrl`:

```json
{ "message": "这张图里有什么？", "imageUrl": "/uploads/chat-images/2026-05-20/uuid.png", "model": "gpt-4o" }
```

- `imageUrl` 非空时，Python 引擎读文件转 base64，构造多模态 LangChain 消息

## 数据模型

### conv_message 表新增字段

```sql
ALTER TABLE conv_message ADD COLUMN image_url VARCHAR(500);
```

### Message.java 实体新增

```java
private String imageUrl;
```

## Python 引擎改动

### /chat/stream（改造）

`ChatRequest` 新增 `imageUrl: Optional[str]`。当有图片时构造多模态消息：

```python
if image_url:
    image_base64 = read_and_encode(image_url)
    msg_content = [
        {"type": "text", "text": query or "请描述这张图片"},
        {"type": "image_url", "image_url": {"url": f"data:image/png;base64,{image_base64}"}}
    ]
    messages.append(HumanMessage(content=msg_content))
else:
    messages.append(HumanMessage(content=query))
```

- 注意：`create_llm()` 已不传 `text_messages_only`，ChatOpenAI 默认支持多模态 content
- 非视觉模型（如 deepseek-chat）收到图片时返回明确的错误提示

### 视觉模型列表（config.py 预置）

```python
vision_models = [
    "gpt-4o", "gpt-4-turbo",        # OpenAI
    "glm-4v",                        # 智谱
    "qwen-vl-plus", "qwen-vl-max",   # 阿里
    "gemini-2.5-flash", "gemini-2.5-pro",  # Google (新增)
]
```

Gemini 通过 OpenAI 兼容接口接入（`https://generativelanguage.googleapis.com/v1beta/openai/`），`MODEL_ROUTES` 同步注册。

### /speech/transcribe（新增）

```python
@app.post("/speech/transcribe")
async def transcribe_audio(file: UploadFile):
    audio_bytes = await file.read()
    client = OpenAI(api_key=settings.openai_api_key)
    result = client.audio.transcriptions.create(
        model="whisper-1",
        file=("audio.webm", audio_bytes, "audio/webm"),
        language="zh"
    )
    return {"text": result.text}
```

### PDF 图片提取（改造 doc_process.py / PdfParser）

Python 侧用 PyMuPDF (`fitz`) 提取 PDF 内嵌图片：

```python
import fitz

def extract_images_from_pdf(file_path: str) -> list[dict]:
    doc = fitz.open(file_path)
    images = []
    for page_num, page in enumerate(doc):
        for img_info in page.get_images(full=True):
            xref = img_info[0]
            base_image = doc.extract_image(xref)
            img_path = save_image(base_image["image"], base_image["ext"])
            images.append({"path": img_path, "page": page_num + 1})
    return images
```

- 提取的图片块走现有 `describe_image()` → GPT-4o 描述 → 向量化入库
- 新增依赖：`PyMuPDF` 加入 `requirements.txt`

Java 侧 `PdfParser.java` 同时改造，用 PDFBox `PDResources` 提取内嵌图片存到 `uploads/images/`。

## Java 后端改动

### agent-conversation 模块

**新增：**
- `service/ImageUploadService.java` — 校验图片类型/大小，生成 UUID 文件名，存到 `uploads/chat-images/{date}/`
- `controller/TranscribeController.java` — 接收音频 multipart，转发到 Python `/speech/transcribe`，返回文本

**修改：**
- `ConversationController.java` — 新增 upload-image 端点，stream 端点接收 imageUrl 透传
- `StreamingProxy.java` — `streamChat()` 参数加 imageUrl，序列化到 Python 请求体
- `ChatServiceImpl.java` — `saveUserMessage()` 支持 imageUrl 字段
- `Message.java` — 新增 `imageUrl` 字段
- `schema.sql` — `conv_message` 表加 `image_url` 列

### agent-knowledge 模块

**修改：**
- `PdfParser.java` — 用 PDFBox 提取内嵌图片，存到 `uploads/images/`，返回图片路径列表

### agent-gateway 模块

**配置：**
```yaml
spring:
  web:
    resources:
      static-locations: file:uploads/
```

**定时清理：**
```java
@Scheduled(cron = "0 0 3 * * ?")  // 每天凌晨3点
public void cleanupOldImages() {
    // 删除 uploads/chat-images/ 下超过 24 小时的文件
}
```

## 前端改动

### 聊天输入区（Chat.tsx 改造）

```
┌──────────────────────────────────────┐
│  [📎] [🎤]                          │  ← 按钮行
│                                      │
│  ┌──────┐ ┌──────┐                  │  ← 图片缩略图预览（80x80，可删除）
│  │ 图1  │ │ 图2  │                  │
│  └──────┘ └──────┘                  │
│                                      │
│  [       文本输入框          ] [→]   │
│                                      │
│  🔴 Recording... 2.5s  [取消] [发送] │  ← 录音状态栏
└──────────────────────────────────────┘
```

### 新增组件

- `components/chat/ImageUploader.tsx` — 图片选择按钮 + 缩略图预览（本地 ObjectURL 先显示）+ 删除 + 后台上传
- `components/chat/AudioRecorder.tsx` — 录音按钮 + MediaRecorder + 计时器 + 转写上传

### 修改文件

- `pages/Chat.tsx` — 集成 ImageUploader + AudioRecorder，消息气泡支持 imageUrl 图片展示
- `api/client.ts` — 新增 `uploadImage(file)` 和 `transcribeAudio(blob)` 方法

### 交互细节

- 图片选中后立刻显示本地缩略图（ObjectURL），同时后台上传
- 上传完成替换为服务器 URL；失败显示错误标记
- 语音录制显示脉冲动画 + 秒数计时
- 转写结果填入输入框，用户可编辑后发送（不自动发送）
- 语音录制可取消（丢弃录音）
- 消息气泡中有 imageUrl 时显示可点击放大的图片缩略图
- 前端无需新增 npm 依赖（MediaRecorder、FileReader 均为浏览器原生 API）

## 新增依赖汇总

| 位置 | 依赖 | 用途 |
|------|------|------|
| `requirements.txt` | `PyMuPDF` | PDF 内嵌图片提取 |
| 前端 | 无 | 全用浏览器原生 API |

## 定时清理

- 每天凌晨 3:00 执行，删除 `uploads/chat-images/` 下修改时间超过 24 小时的文件
- 使用 Spring `@Scheduled` + `java.nio.file.Files.walkFileTree`

## 错误处理

- 非视觉模型收到图片 → Python 引擎返回错误 SSE 事件："模型 xxx 不支持图片输入，请切换到 gpt-4o / glm-4v / qwen-vl"
- 图片文件不存在 → Python 返回 400
- 图片过大（>10MB）→ Java 返回 413
- 图片格式不支持 → Java 返回 400
- Whisper 转写失败 → Python 返回 500，前端提示重试
