package com.agent.knowledge.parser;

import java.io.InputStream;
import java.util.Map;

// 文档解析器接口, 每种文件格式一个实现
public interface DocumentParser {
    // 返回支持的格式标识
    String supportedType();
    // 解析文件, 返回 "text" → 文本内容, "images" → 图片路径, "needsVision" → 是否需多模态
    Map<String, String> parse(InputStream inputStream, String fileName) throws Exception;
}
