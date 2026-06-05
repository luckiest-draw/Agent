# 外部 JSON 测试套件加载器
import json
import os
import logging

logger = logging.getLogger("suite_loader")

SUITES_DIR = os.path.join(os.path.dirname(__file__), "test_suites")


def list_suites() -> list[dict]:
    """列出所有可用的测试套件"""
    suites = []
    if not os.path.isdir(SUITES_DIR):
        return suites
    for fname in sorted(os.listdir(SUITES_DIR)):
        if fname.endswith(".json"):
            path = os.path.join(SUITES_DIR, fname)
            try:
                with open(path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                suites.append({
                    "file": fname,
                    "name": data.get("name", fname),
                    "description": data.get("description", ""),
                    "type": data.get("type", "unknown"),
                    "case_count": len(data.get("cases", [])),
                })
            except Exception as e:
                logger.warning("Failed to load suite %s: %s", fname, e)
    return suites


def load_suite(filename: str) -> dict:
    """加载指定套件，返回 {name, type, cases}"""
    fname = filename if filename.endswith(".json") else filename + ".json"
    path = os.path.join(SUITES_DIR, fname)
    if not os.path.isfile(path):
        raise FileNotFoundError(f"Suite not found: {fname} (looked in {SUITES_DIR})")
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def load_all_suites() -> list[dict]:
    """加载全部套件"""
    suites = []
    for info in list_suites():
        try:
            suites.append(load_suite(info["file"]))
        except Exception as e:
            logger.warning("Skip %s: %s", info["file"], e)
    return suites
