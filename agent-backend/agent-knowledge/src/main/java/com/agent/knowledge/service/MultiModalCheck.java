package com.agent.knowledge.service;

import org.springframework.stereotype.Component;
import java.util.Set;

// 多模态能力检查: 判断模型是否支持图像处理
@Component
public class MultiModalCheck {

    // 支持多模态的模型列表(可从配置读取)
    private static final Set<String> VISION_MODELS = Set.of(
        "gpt-4o", "gpt-4-turbo", "gpt-4-vision",
        "glm-4v", "glm-4",
        "qwen-vl-plus", "qwen-vl-max"
    );

    public boolean supportsVision(String modelName) {
        return modelName != null && VISION_MODELS.contains(modelName.toLowerCase());
    }

    // 返回推荐的fallback模型
    public String getTextOnlyModel() {
        return "deepseek-chat";
    }
}
