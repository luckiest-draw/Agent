package com.agent.knowledge.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Component
public class PdfParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(PdfParser.class);

    @Override public String supportedType() { return "pdf"; }

    @Override
    public Map<String, String> parse(InputStream inputStream, String fileName) throws Exception {
        Map<String, String> result = new HashMap<>();
        byte[] bytes = inputStream.readAllBytes();

        try (PDDocument doc = Loader.loadPDF(bytes)) {
            // 1. 提取文本
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            result.put("text", text != null ? text.trim() : "");

            // 2. 提取内嵌图片
            List<String> imagePaths = new ArrayList<>();
            Path uploadDir = Paths.get("uploads", "images");
            Files.createDirectories(uploadDir);

            int imageIndex = 0;
            for (PDPage page : doc.getPages()) {
                PDResources resources = page.getResources();
                if (resources == null) continue;
                for (COSName name : resources.getXObjectNames()) {
                    if (!resources.isImageXObject(name)) continue;
                    try {
                        PDImageXObject image = (PDImageXObject) resources.getXObject(name);
                        BufferedImage bufferedImage = image.getImage();
                        String imgName = UUID.randomUUID().toString() + ".png";
                        Path imgPath = uploadDir.resolve(imgName);
                        ImageIO.write(bufferedImage, "png", imgPath.toFile());
                        imagePaths.add(imgPath.toString().replace("\\", "/"));
                        imageIndex++;
                    } catch (Exception e) {
                        log.warn("Failed to extract image {} from PDF: {}", imageIndex, e.getMessage());
                    }
                }
            }

            if (!imagePaths.isEmpty()) {
                result.put("images", String.join(";", imagePaths));
                result.put("needsVision", "true");
                log.info("Extracted {} images from PDF: {}", imagePaths.size(), fileName);
            }
        }
        return result;
    }
}
