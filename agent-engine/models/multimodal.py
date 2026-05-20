# Multimodal utilities: read image file, build vision model messages
import base64
import logging
from pathlib import Path

logger = logging.getLogger("multimodal")

IMAGE_EXT_TO_MIME = {
    "jpg": "image/jpeg", "jpeg": "image/jpeg",
    "png": "image/png",
    "gif": "image/gif",
    "webp": "image/webp",
    "bmp": "image/bmp",
}

# Default prompt when user sends image without text
_FALLBACK_TEXT = "请描述这张图片"


def encode_image_to_base64(image_path: str) -> str:
    p = Path(image_path)
    candidates = []
    if p.is_absolute() and p.exists():
        candidates.append(p)
    # Project-relative: resolve from agent-gateway/uploads/...
    project_root = Path(__file__).parent.parent.parent.parent
    candidates.append(project_root / "agent-gateway" / image_path.lstrip("/"))

    abs_path = None
    for c in candidates:
        if c.exists():
            abs_path = c
            logger.debug("Found image at: %s", abs_path)
            break

    if abs_path is None:
        logger.error("Image not found: %s (tried: %s)", image_path, candidates)
        raise FileNotFoundError(f"Image not found: {image_path} (tried: {candidates})")

    ext = abs_path.suffix.lstrip(".").lower()
    mime = IMAGE_EXT_TO_MIME.get(ext, "image/png")

    with open(abs_path, "rb") as f:
        data = base64.b64encode(f.read()).decode("utf-8")
    return f"data:{mime};base64,{data}"


def build_multimodal_content(text: str, image_url: str) -> list:
    data_uri = encode_image_to_base64(image_url)
    return [
        {"type": "text", "text": text or _FALLBACK_TEXT},
        {"type": "image_url", "image_url": {"url": data_uri}},
    ]
