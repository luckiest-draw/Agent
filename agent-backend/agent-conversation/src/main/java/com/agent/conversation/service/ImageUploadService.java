package com.agent.conversation.service;

import com.agent.common.BusinessException;
import com.agent.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

@Service
public class ImageUploadService {

    private static final Logger log = LoggerFactory.getLogger(ImageUploadService.class);
    private static final Set<String> ALLOWED_TYPES = Set.of(
        "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp"
    );
    private static final long MAX_SIZE = 10 * 1024 * 1024; // 10MB
    private static final String UPLOAD_DIR = "uploads/chat-images";

    public record ImageUploadResult(String imageUrl, String fileName, long size) {}

    public ImageUploadResult upload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片大小不能超过 10MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_UNSUPPORTED,
                "不支持的图片格式: " + contentType + "，支持 JPG/PNG/GIF/WebP/BMP");
        }

        String ext = getExtension(contentType, file.getOriginalFilename());
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String fileName = UUID.randomUUID().toString() + "." + ext;

        try {
            Path uploadDir = Paths.get(UPLOAD_DIR, dateDir);
            Files.createDirectories(uploadDir);
            Path targetPath = uploadDir.resolve(fileName);
            file.transferTo(targetPath);

            String imageUrl = "/" + UPLOAD_DIR + "/" + dateDir + "/" + fileName;
            log.info("Image uploaded: {} ({} bytes)", imageUrl, file.getSize());
            return new ImageUploadResult(
                imageUrl.replace("\\", "/"),
                file.getOriginalFilename(),
                file.getSize()
            );
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "图片上传失败: " + e.getMessage());
        }
    }

    private String getExtension(String contentType, String fileName) {
        if (contentType.equals("image/jpeg")) return "jpg";
        if (contentType.equals("image/png")) return "png";
        if (contentType.equals("image/gif")) return "gif";
        if (contentType.equals("image/webp")) return "webp";
        if (contentType.equals("image/bmp")) return "bmp";
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        }
        return "png";
    }
}
