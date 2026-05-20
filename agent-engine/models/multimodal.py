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
