package com.agent.knowledge.parser;

import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// 图片解析器: 保存图片到本地, 标记需要多模态模型处理
@Component
public class ImageParser implements DocumentParser {
    @Override public String supportedType() { return "image"; }

    // 支持的图片扩展名
    public static final String[] SUPPORTED_EXTENSIONS = {"jpg", "jpeg", "png", "gif", "bmp"};

    @Override
    public Map<String, String> parse(InputStream inputStream, String fileName) throws Exception {
        Map<String, String> result = new HashMap<>();

        // 生成唯一文件名,保存到upload目录
        String ext = getExtension(fileName);
        String storedName = UUID.randomUUID().toString() + "." + ext;
        Path uploadDir = Paths.get("uploads", "images");
        Files.createDirectories(uploadDir);
        Path targetPath = uploadDir.resolve(storedName);
        Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);

        result.put("text", "");
        result.put("images", targetPath.toString());
        result.put("needsVision", "true");
        return result;
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "png";
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}
