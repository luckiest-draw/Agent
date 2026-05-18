# Multi-Model Manager: OpenAI-compatible API for all supported models
from langchain_openai import ChatOpenAI
from config import settings

# Model routing: model name → (base_url, api_key)
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
}


def create_llm(model_name: str = None, temperature: float = 0.7, max_tokens: int = 2048, streaming: bool = False):
    """创建LangChain ChatOpenAI实例,支持多模型路由"""
    model = model_name or settings.default_model
    route = MODEL_ROUTES.get(model)
    if not route:
        # fallback: use as-is with DeepSeek
        route = (settings.deepseek_base_url, settings.deepseek_api_key)

    base_url, api_key = route
    return ChatOpenAI(
        model=model,
        openai_api_key=api_key or "not-needed",
        openai_api_base=base_url,
        temperature=temperature,
        max_tokens=max_tokens,
        streaming=streaming,
    )


def supports_vision(model_name: str) -> bool:
    return model_name in settings.vision_models


def get_model_string(model_name: str) -> str:
    return model_name or settings.default_model
