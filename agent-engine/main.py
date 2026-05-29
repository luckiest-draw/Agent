# Agent Engine: FastAPI + LangChain AI 引擎
import json
import logging
from fastapi import FastAPI, Request, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
from typing import Optional, List, Dict
from config import settings

from models.model_manager import create_llm, supports_vision, get_model_string
from agents.chat_agent import chat_stream, rag_chat_stream
from rag.vector_store import add_documents
from rag.retriever import retrieve_context

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("agent-engine")

app = FastAPI(title="Agent AI Engine", version="1.0.0")


# ==================== Request/Response Models ====================

class ChatRequest(BaseModel):
    query: str
    history: List[Dict] = []
    model: Optional[str] = None
    systemPrompt: Optional[str] = None
    temperature: float = 0.7
    maxTokens: int = 2048
    useRag: bool = False
    topK: int = 5
    imageUrl: Optional[str] = None
    tools: List[str] = []
    skillName: Optional[str] = None
    conversationId: Optional[str] = None


class EmbeddingRequest(BaseModel):
    texts: List[str]
    metadata: Optional[List[Dict]] = None


class WorkflowExecuteRequest(BaseModel):
    workflowDef: dict
    model: Optional[str] = None


# ==================== Health ====================

@app.get("/health")
def health():
    return {"status": "ok", "model": settings.default_model}


# ==================== Chat (SSE Streaming) ====================

@app.post("/chat/stream")
async def chat_stream_endpoint(request: ChatRequest):
    """流式对话,Server-Sent Events"""
    async def event_generator():
        try:
            async for content in chat_stream(
                query=request.query,
                history=request.history,
                model=request.model,
                system_prompt=request.systemPrompt,
                temperature=request.temperature,
                image_url=request.imageUrl,
            ):
                yield f"data: {json.dumps({'content': content, 'done': False})}\n\n"
            yield f"data: {json.dumps({'content': '', 'done': True})}\n\n"
        except Exception as e:
            logger.error(f"Chat stream error: {e}")
            yield f"data: {json.dumps({'error': str(e), 'done': True})}\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


@app.post("/chat/rag")
async def rag_chat_endpoint(request: ChatRequest):
    """RAG增强对话"""
    async def event_generator():
        try:
            async for content in rag_chat_stream(
                query=request.query,
                history=request.history,
                model=request.model,
                system_prompt=request.systemPrompt,
                temperature=request.temperature,
                top_k=request.topK,
                image_url=request.imageUrl,
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


# ==================== Skill Agent (Tool Calling) ====================

@app.post("/chat/agent")
async def skill_chat_endpoint(request: ChatRequest):
    """带工具调用的 Agent 流式对话"""
    from agents.skill_agent import skill_chat_stream

    async def event_generator():
        try:
            async for sse in skill_chat_stream(
                query=request.query,
                history=request.history,
                tools=request.tools,
                system_prompt=request.systemPrompt,
                model=request.model,
                temperature=request.temperature,
                conversation_id=request.conversationId or "default",
            ):
                yield sse
        except Exception as e:
            logger.error(f"Skill agent error: {e}")
            yield f"data: {json.dumps({'error': str(e), 'done': True})}\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "Connection": "keep-alive", "X-Accel-Buffering": "no"},
    )


@app.post("/skills/route")
def route_skill(request: dict):
    """根据用户消息路由到最匹配的 Skill（基于 embedding 相似度）"""
    from rag.vector_store import create_embeddings
    query = request.get("query", "")
    skills = request.get("skills", [])  # [{"name": "...", "description": "..."}, ...]

    if not skills or not query:
        return {"skill": None, "score": 0.0, "reason": "no skills or empty query"}

    emb = create_embeddings()
    query_vec = emb.embed_query(query)
    scores = []
    for s in skills:
        desc = s.get("description", "")
        if not desc:
            scores.append((s["name"], 0.0))
            continue
        skill_vec = emb.embed_query(desc)
        dot = sum(a * b for a, b in zip(query_vec, skill_vec))
        norm_a = sum(a * a for a in query_vec) ** 0.5
        norm_b = sum(b * b for b in skill_vec) ** 0.5
        sim = dot / (norm_a * norm_b) if norm_a > 0 and norm_b > 0 else 0.0
        scores.append((s["name"], sim))

    scores.sort(key=lambda x: x[1], reverse=True)
    best = scores[0]
    threshold = request.get("threshold", 0.7)
    if best[1] >= threshold:
        return {"skill": best[0], "score": round(best[1], 4), "matched": True}
    return {"skill": None, "score": round(best[1], 4), "matched": False,
            "reason": f"best match '{best[0]}' below threshold {threshold}"}


@app.get("/skills/tools")
def list_skill_tools():
    """列出所有内置工具"""
    from tools.tool_registry import list_tools
    return {"tools": list_tools()}


# ==================== RAG / Knowledge ====================

@app.post("/rag/retrieve")
def rag_retrieve(query: str, top_k: int = 5):
    """检索相关文档"""
    docs = retrieve_context(query, top_k)
    return {"query": query, "documents": docs, "count": len(docs)}


@app.post("/rag/embed")
def embed_texts(request: EmbeddingRequest):
    """文本向量化入库"""
    docs = []
    metas = request.metadata or [{}] * len(request.texts)
    for i, text in enumerate(request.texts):
        meta = metas[i] if i < len(metas) else {}
        docs.append((text, meta))
    count = add_documents(docs)
    return {"status": "done", "count": count}


# ==================== Memory Support ====================

class SingleEmbedRequest(BaseModel):
    text: str

@app.post("/rag/embed-single")
def embed_single(request: SingleEmbedRequest):
    """获取单文本的 embedding 向量，不存入 pgvector（话题检测用）"""
    from rag.vector_store import create_embeddings
    emb = create_embeddings()
    vector = emb.embed_query(request.text)
    return {"embedding": vector, "dimensions": len(vector)}


class SummarizeRequest(BaseModel):
    messages: List[Dict[str, str]]  # [{"role": "user/assistant", "content": "..."}]
    model: Optional[str] = None

@app.post("/memory/summarize")
def summarize_messages(request: SummarizeRequest):
    """将旧消息压缩为 ~200 词的摘要"""
    llm = create_llm(model_name=request.model, temperature=0.3, max_tokens=512, streaming=False)
    conversation = "\n".join(
        f"{'User' if m.get('role') == 'user' else 'Assistant'}: {m.get('content', '')}"
        for m in request.messages
    )
    prompt = (
        "Summarize the following conversation fragment into a concise paragraph (under 200 words) "
        "that captures all key topics, facts, and decisions. Write in the same language as the conversation:\n\n"
        f"{conversation}"
    )
    result = llm.invoke(prompt)
    summary = result.content if hasattr(result, "content") else str(result)
    return {"summary": summary}


# ==================== Speech-to-Text ====================

from fastapi import UploadFile, File

@app.post("/speech/transcribe")
async def transcribe_audio(file: UploadFile = File(...)):
    """语音转文字,调用 OpenAI Whisper API"""
    from openai import OpenAI
    audio_bytes = await file.read()
    api_key = settings.openai_api_key
    if not api_key:
        raise HTTPException(status_code=500, detail="请配置 OPENAI_API_KEY，Whisper 必须使用 OpenAI API")
    client = OpenAI(
        api_key=api_key,
        base_url="https://api.openai.com/v1",
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


# ==================== Model Info ====================

@app.get("/models/supported")
def supported_models():
    """返回支持的模型列表"""
    from models.model_manager import MODEL_ROUTES
    return {
        "models": [
            {
                "name": name,
                "vision": supports_vision(name),
            }
            for name in MODEL_ROUTES.keys()
        ],
        "default": settings.default_model,
    }


# ==================== Workflow ====================

@app.post("/workflow/execute")
def execute_workflow_endpoint(request: WorkflowExecuteRequest):
    """同步执行工作流(简单DAG)"""
    from workflows.workflow_executor import execute_workflow
    try:
        result = execute_workflow(request.workflowDef, request.model)
        return {"status": "done", "result": result}
    except Exception as e:
        logger.error(f"Workflow error: {e}")
        return {"status": "failed", "error": str(e)}


# ==================== Celery Task Dispatch ====================

@app.post("/tasks/doc-process")
def dispatch_doc_process(chunk_id: int, content: str, chunk_type: str = "text",
                         image_path: str = None, metadata: dict = None):
    """派发文档处理任务到Celery"""
    from tasks.doc_process import process_document
    task = process_document.delay(chunk_id, content, chunk_type, image_path, metadata)
    return {"taskId": task.id, "status": "dispatched"}


@app.post("/tasks/workflow-exec")
def dispatch_workflow_exec(workflow_def: dict, model: str = None):
    """派发工作流执行任务到Celery"""
    from tasks.workflow_exec import execute_workflow_task
    task = execute_workflow_task.delay(workflow_def, model)
    return {"taskId": task.id, "status": "dispatched"}


# ==================== Startup ====================

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host=settings.host, port=settings.port, reload=True,
                reload_dirs=["agents", "models", "rag", "workflows", "tasks"],
                reload_excludes=["*.pyc", "__pycache__", "venv", ".git"])
