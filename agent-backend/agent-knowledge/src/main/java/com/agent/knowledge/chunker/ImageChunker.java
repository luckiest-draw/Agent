package com.agent.knowledge.chunker;

import org.springframework.stereotype.Component;

// 图片分片器: 每张图片作为一个独立chunk, 标记需多模态处理
@Component
public class ImageChunker {

    public String chunk(String imagePath) {
        // 图片不分片,返回图片路径作为单个chunk的content
        return imagePath;
    }
}
