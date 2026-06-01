# 三态熔断器 + 模型健康监控 + 优先级降级链
import time
import threading
import logging
from enum import Enum

logger = logging.getLogger("circuit_breaker")


class State(Enum):
    CLOSED = "closed"        # 正常，请求直达
    OPEN = "open"            # 熔断，直接拒绝
    HALF_OPEN = "half_open"  # 半开，试探一个请求


class CircuitBreaker:
    """单模型熔断器"""

    def __init__(self, name: str, failure_threshold: int = 3,
                 cooldown_seconds: float = 30.0):
        self.name = name
        self.state = State.CLOSED
        self.failure_count = 0
        self.success_count = 0
        self.last_failure_time = 0.0
        self.last_probe_time = 0.0
        self.failure_threshold = failure_threshold
        self.cooldown_seconds = cooldown_seconds
        self._initial_cooldown = cooldown_seconds
        self._lock = threading.Lock()

    def allow_request(self) -> bool:
        with self._lock:
            if self.state == State.CLOSED:
                return True
            if self.state == State.OPEN:
                elapsed = time.time() - self.last_failure_time
                if elapsed >= self.cooldown_seconds:
                    self.state = State.HALF_OPEN
                    self.last_probe_time = time.time()
                    logger.info("Circuit %s → HALF_OPEN (cooldown %.1fs elapsed)",
                                self.name, elapsed)
                    return True
                return False
            # HALF_OPEN: 只允许一个试探请求
            return True

    def record_success(self):
        with self._lock:
            if self.state == State.HALF_OPEN:
                logger.info("Circuit %s → CLOSED (probe succeeded)", self.name)
            self.state = State.CLOSED
            self.failure_count = 0
            self.cooldown_seconds = self._initial_cooldown
            self.success_count += 1

    def record_failure(self):
        with self._lock:
            self.failure_count += 1
            self.last_failure_time = time.time()
            if self.state == State.HALF_OPEN:
                self.state = State.OPEN
                self.cooldown_seconds = min(self.cooldown_seconds * 2, 300.0)
                logger.warning(
                    "Circuit %s → OPEN (probe failed, cooldown %.0fs)",
                    self.name, self.cooldown_seconds)
            elif self.failure_count >= self.failure_threshold:
                self.state = State.OPEN
                logger.warning(
                    "Circuit %s → OPEN (%d failures, cooldown %.0fs)",
                    self.name, self.failure_count, self.cooldown_seconds)

    def is_open(self) -> bool:
        return self.state == State.OPEN

    def status(self) -> dict:
        with self._lock:
            return {
                "name": self.name,
                "state": self.state.value,
                "failures": self.failure_count,
                "successes": self.success_count,
                "cooldown_s": self.cooldown_seconds,
            }


class ModelHealthMonitor:
    """全局模型健康监控"""

    def __init__(self):
        self._breakers: dict[str, CircuitBreaker] = {}
        self._lock = threading.Lock()

    def get_breaker(self, model_name: str) -> CircuitBreaker:
        with self._lock:
            if model_name not in self._breakers:
                self._breakers[model_name] = CircuitBreaker(model_name)
            return self._breakers[model_name]

    def allow(self, model_name: str) -> bool:
        return self.get_breaker(model_name).allow_request()

    def success(self, model_name: str):
        self.get_breaker(model_name).record_success()

    def failure(self, model_name: str):
        self.get_breaker(model_name).record_failure()

    def is_model_open(self, model_name: str) -> bool:
        return self.get_breaker(model_name).is_open()

    def all_status(self) -> list[dict]:
        with self._lock:
            return [b.status() for b in self._breakers.values()]


# 全局单例
health_monitor = ModelHealthMonitor()

# 优先级降级链：每个模型的备选列表（按优先级排序）
FALLBACK_CHAIN: dict[str, list[str]] = {
    "gpt-4o":          ["deepseek-v4-pro", "qwen-max"],
    "gpt-4-turbo":     ["gpt-4o", "deepseek-v4-pro"],
    "gpt-3.5-turbo":   ["deepseek-chat", "qwen-plus"],
    "gemini-2.5-pro":  ["gemini-2.5-flash", "deepseek-v4-pro"],
    "gemini-2.5-flash": ["deepseek-v4-pro", "qwen-max"],
    "glm-4":           ["deepseek-v4-pro", "qwen-max"],
    "glm-4v":          ["gpt-4o", "qwen-vl-max"],
    "qwen-max":        ["deepseek-v4-pro", "glm-4"],
    "qwen-vl-max":     ["gpt-4o", "qwen-vl-plus"],
    "qwen-vl-plus":    ["gpt-4o", "deepseek-v4-pro"],
    "qwen-plus":       ["deepseek-chat", "glm-4"],
    "deepseek-v4-pro": ["deepseek-chat", "qwen-max"],
    "deepseek-chat":   ["qwen-plus", "glm-4"],
    "deepseek-reasoner": ["deepseek-v4-pro", "deepseek-chat"],
}

# 降级时的能力退让配置
DEGRADE_CONFIG = {
    "default":  {"temperature": 0.7, "max_tokens": 2048},
    "degrade_1": {"temperature": 0.3, "max_tokens": 1024},
    "degrade_2": {"temperature": 0.0, "max_tokens": 512},
}


def resolve_fallback(model_name: str) -> list[str]:
    """从降级链获取备选模型列表（含原模型作为首选项）"""
    chain = FALLBACK_CHAIN.get(model_name, [])
    return [model_name] + chain


def get_degrade_config(level: int) -> dict:
    """获取降级配置：level 0=正常, 1=轻度降级, 2=重度降级"""
    if level == 1:
        return DEGRADE_CONFIG["degrade_1"]
    elif level >= 2:
        return DEGRADE_CONFIG["degrade_2"]
    return DEGRADE_CONFIG["default"]
