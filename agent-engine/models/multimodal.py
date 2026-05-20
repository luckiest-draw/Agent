# Multimodal utilities: read image file, build vision model messages
import base64
from pathlib import Path

IMAGE_EXT_TO_MIME = {
    "jpg": "image/jpeg", "jpeg": "image/jpeg",
    "png": "image/png",
    "gif": "image/gif",
    "webp": "image/webp",
    "bmp": "image/bmp",
}


def encode_image_to_base64(image_path: str) -> str:
    candidates = [
        Path(image_path),
        Path(__file__).parent.parent.parent.parent / "agent-gateway" / image_path,
    ]
    abs_path = None
    for p in candidates:
        if p.exists():
            abs_path = p
            break
    if abs_path is None:
        raise FileNotFoundError(f"Image not found: {image_path}")

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
