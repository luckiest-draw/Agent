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
    """根据用户消息路由到最匹配的 Skill（LLM 分类 + embedding 降级）"""
    query = request.get("query", "")
    skills = request.get("skills", [])  # [{"name": "...", "description": "..."}, ...]

    if not skills or not query:
        return {"skill": None, "score": 0.0, "reason": "no skills or empty query"}

    # 优先用 LLM 做路由（不需要 OpenAI key）
    try:
        from models.model_manager import create_llm
        skill_lines = "\n".join(
            f"- {s['name']}: {s.get('description', '')[:100]}"
            for s in skills
        )
        llm = create_llm(model_name="deepseek-chat", temperature=0.0, max_tokens=64, streaming=False)
        resp = llm.invoke(
            f"用户说：{query[:200]}\n\n"
            f"以下是可以用的技能：\n{skill_lines}\n\n"
            f"请选择最适合处理这个请求的技能。只输出技能名称，不要其他内容。"
            f"如果都不合适，输出 none。"
        )
        skill_name = resp.content.strip() if hasattr(resp, "content") else str(resp).strip()
        if skill_name and skill_name.lower() != "none":
            for s in skills:
                if s["name"] == skill_name:
                    return {"skill": skill_name, "score": 1.0, "matched": True,
                            "method": "llm"}
    except Exception as e:
        logger.warning("LLM routing failed, trying embedding: %s", e)

    # 降级：embedding 相似度匹配（需要 OpenAI key 且 DeepSeek 不支持）
    try:
        from rag.vector_store import create_embeddings
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
            return {"skill": best[0], "score": round(best[1], 4), "matched": True,
                    "method": "embedding"}
    except Exception as e2:
        logger.warning("Embedding routing also failed: %s", e2)

    return {"skill": None, "score": 0.0, "matched": False,
            "reason": "routing unavailable"}


@app.get("/skills/tools")
async def list_all_tools():
    """列出所有工具（内置 + MCP）"""
    from tools.tool_registry import list_all_tools
    return {"tools": await list_all_tools()}


@app.get("/mcp/servers")
def list_mcp_servers():
    """列出已配置的 MCP 服务器"""
    from tools.mcp_manager import list_mcp_servers, get_mcp_tool_names
    return {"servers": list_mcp_servers(), "loaded_tools": get_mcp_tool_names()}


@app.get("/models/health")
def models_health():
    """三态熔断器状态：查看各模型的健康状态"""
    from models.circuit_breaker import health_monitor
    statuses = health_monitor.all_status()
    return {"models": statuses, "total": len(statuses)}


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


# ==================== Evaluation ====================

@app.get("/eval/summary")
def eval_summary():
    """评测用例概览"""
    from eval.test_cases import summary as tests_summary
    return tests_summary()


@app.get("/eval/suites")
def eval_suites():
    """列出可用的外部 JSON 测试套件"""
    from eval.suite_loader import list_suites
    return {"suites": list_suites()}


@app.post("/eval/run")
async def eval_run(request: dict):
    """加载外部套件并评测
    Body: {"suite": "platform_knowledge", "model": "deepseek-v4-pro"}
    不传 suite 则跑全部套件
    """
    from eval.suite_loader import load_suite, load_all_suites, list_suites
    from eval.evaluators import evaluate_rag
    import datetime
    from models.model_manager import create_llm
    from langchain_core.messages import HumanMessage, SystemMessage
    import os
    import json

    suite_name = request.get("suite") if request else None
    model_name = request.get("model", "deepseek-chat") if request else "deepseek-chat"

    if suite_name:
        try:
            suites = [load_suite(suite_name)]
        except FileNotFoundError as e:
            return {"error": str(e), "available": [s["file"] for s in list_suites()]}
    else:
        suites = load_all_suites()

    if not suites:
        return {"error": "No suites found",
                "directory": os.path.abspath("eval/test_suites")}

    all_reports = []
    llm = create_llm(model_name=model_name, temperature=0.0, streaming=False)

    for suite in suites:
        stype = suite.get("type", "rag")
        cases = suite.get("cases", [])
        results = []

        for i, case in enumerate(cases):
            query = case["query"]
            logger.info("[%s] %d/%d: %s", suite.get("name", ""), i + 1, len(cases), query[:50])

            # 构建上下文
            contexts = case.get("expected_context", [])
            if contexts:
                sys_prompt = "根据以下参考上下文回答问题，不要编造:\n" + "\n".join(contexts)
            else:
                sys_prompt = "根据你的知识回答问题，不要编造"

            resp = llm.invoke([SystemMessage(content=sys_prompt), HumanMessage(content=query)])
            answer = resp.content if hasattr(resp, "content") else str(resp)

            if stype == "rag":
                scores = evaluate_rag(query, answer, contexts)
            elif stype == "agent":
                from eval.evaluators import evaluate_agent
                tools_called = _detect_tool_intent(query, answer)
                scores = evaluate_agent(case, answer, tools_called)
            elif stype == "hallucination":
                from eval.evaluators import evaluate_hallucination
                scores = evaluate_hallucination(case, answer)
            else:
                scores = {}

            scores["query"] = query
            scores["answer"] = answer[:300]
            results.append(scores)

        # 统计
        avg = {}
        if stype == "rag":
            avg = {
                "faithfulness": sum(r.get("faithfulness", 0) for r in results) / len(results),
                "answer_relevancy": sum(r.get("answer_relevancy", 0) for r in results) / len(results),
                "context_precision": sum(r.get("context_precision", 0) for r in results) / len(results) if results[0].get("context_precision") is not None else None,
                "context_recall": sum(r.get("context_recall", 0) for r in results) / len(results) if results[0].get("context_recall") is not None else None,
            }

        report = {
            "suite": suite.get("name"),
            "type": stype,
            "model": model_name,
            "timestamp": datetime.datetime.now().isoformat(),
            "case_results": results,
            "averages": avg,
            "total_cases": len(results),
        }

        # 落盘
        os.makedirs("eval/reports", exist_ok=True)
        ts = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
        path = f"eval/reports/{suite.get('name', 'suite')}_{ts}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(report, f, ensure_ascii=False, indent=2)
        logger.info("Report saved: %s", path)

        all_reports.append({
            "suite": suite.get("name"),
            "report_file": path,
            "averages": avg,
            "cases": len(results),
        })

    return {
        "model": model_name,
        "reports": all_reports,
        "total_suites": len(all_reports),
    }


@app.post("/eval/rag")
async def eval_rag(request: dict = None):
    """RAG 评测：faithfulness + relevancy + precision + recall + 事实准确率
    评测时使用测试用例自带的期望 context 作为模拟召回片段。
    如需测试真实 RAG 检索质量，请先往知识库入库文档并设置 OPENAI_API_KEY。
    """
    from eval.evaluators import run_rag_eval
    from eval.test_cases import load_rag_cases
    from models.model_manager import create_llm
    from langchain_core.messages import HumanMessage, SystemMessage

    # 构建 query → expected_context 映射
    case_map = {c["query"]: c for c in load_rag_cases()}

    async def rag_adapter(query: str):
        case = case_map.get(query, {})
        # 用测试用例内置的期望 context 作为模拟召回
        contexts = case.get("expected_context", [])
        llm = create_llm(model_name="deepseek-chat", temperature=0.0, streaming=False)
        if contexts:
            rag_sys = "根据以下参考上下文回答问题，不要编造:\n" + "\n".join(ctx for ctx in contexts)
        else:
            rag_sys = "根据你的知识回答问题，不要编造"
        resp = llm.invoke([SystemMessage(content=rag_sys), HumanMessage(content=query)])
        answer = resp.content if hasattr(resp, "content") else str(resp)
        return answer, contexts

    result = await run_rag_eval(rag_adapter)
    return result


@app.post("/eval/agent")
async def eval_agent(request: dict = None):
    """Agent 评测：工具选择准确率 + 工具利用率 + 安全边界"""
    from eval.evaluators import run_agent_eval

    async def agent_adapter(query: str):
        from agents.chat_agent import chat_stream
        response = ""
        async for token in chat_stream(query, [], "deepseek-chat", None):
            response += token
        # Agent 模式下尝试检测工具调用意图
        tools_called = _detect_tool_intent(query, response)
        return response, tools_called

    result = await run_agent_eval(agent_adapter)
    return result


@app.post("/eval/hallucination")
async def eval_hallucination(request: dict = None):
    """幻觉率评测：检测模型是否对未知问题编造答案"""
    from eval.evaluators import run_hallucination_eval

    async def halluc_adapter(query: str):
        from agents.chat_agent import chat_stream
        response = ""
        async for token in chat_stream(query, [], "deepseek-chat", None):
            response += token
        return response, []

    result = await run_hallucination_eval(halluc_adapter)
    return result


@app.post("/eval/all")
async def eval_all(request: dict = None):
    """一键跑全部评测"""
    from eval.evaluators import run_rag_eval, run_agent_eval, run_hallucination_eval

    from eval.test_cases import load_rag_cases
    case_map = {c["query"]: c for c in load_rag_cases()}

    # RAG
    async def rag_fn(query):
        from langchain_core.messages import HumanMessage, SystemMessage
        case = case_map.get(query, {})
        contexts = case.get("expected_context", [])
        rag_sys = "根据以下参考上下文回答问题，不要编造:\n" + "\n".join(ctx for ctx in contexts)
        llm = create_llm(model_name="deepseek-chat", temperature=0.0, streaming=False)
        resp = llm.invoke([SystemMessage(content=rag_sys), HumanMessage(content=query)])
        return resp.content if hasattr(resp, "content") else str(resp), contexts

    async def ag_fn(query):
        from agents.chat_agent import chat_stream
        resp = ""
        async for t in chat_stream(query, [], "deepseek-chat", None):
            resp += t
        return resp, _detect_tool_intent(query, resp)

    rag_result = await run_rag_eval(rag_fn)
    agent_result = await run_agent_eval(ag_fn)
    halluc_result = await run_hallucination_eval(ag_fn)

    return {
        "rag": {"averages": rag_result["averages"], "cases": rag_result["total_cases"]},
        "agent": {"averages": agent_result["averages"], "cases": agent_result["total_cases"]},
        "hallucination": {"rate": halluc_result["hallucination_rate"],
                          "cases": halluc_result["total_cases"]},
    }


def _detect_tool_intent(query: str, response: str) -> list[str]:
    """简易工具意图检测（基于关键词，不实际调工具）"""
    tools = []
    qr = (query + " " + response).lower()
    if any(w in qr for w in ["搜索", "查询", "检索", "搜一下", "查一下", "搜"]):
        tools.append("web_search")
    if any(w in qr for w in ["百科", "维基", "wiki"]):
        tools.append("wikipedia")
    if any(w in qr for w in ["论文", "文献", "arxiv", "学术"]):
        tools.append("arxiv")
    if any(w in qr for w in ["读", "read", "文件", "目录", "list"]):
        tools.append("read_file")
    return tools


# ==================== Startup ====================

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host=settings.host, port=settings.port, reload=True,
                reload_dirs=["agents", "models", "rag", "workflows", "tasks"],
                reload_excludes=["*.pyc", "__pycache__", "venv", ".git"])
