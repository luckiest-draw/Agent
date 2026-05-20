package com.agent.knowledge.service.impl;

import com.agent.knowledge.service.DocIngestService;

import com.agent.knowledge.chunker.ImageChunker;
import com.agent.knowledge.chunker.TextChunker;
import com.agent.knowledge.entity.Chunk;
import com.agent.common.BusinessException;
import com.agent.common.ErrorCode;
import com.agent.knowledge.entity.Document;
import com.agent.knowledge.parser.DocumentParser;
import com.agent.knowledge.mapper.ChunkMapper;
import com.agent.knowledge.mapper.DocumentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Service
public class DocIngestServiceImpl implements DocIngestService {

    private static final Logger log = LoggerFactory.getLogger(DocIngestServiceImpl.class);

    @Autowired
    private List<DocumentParser> parsers;

    @Autowired
    private TextChunker textChunker;

    @Autowired
    private ImageChunker imageChunker;

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private ChunkMapper chunkMapper;

    @Override
    @Transactional
    public Document ingest(MultipartFile file, Long tenantId) throws Exception {
        String fileName = file.getOriginalFilename();
        String ext = getFileExtension(fileName);

        Document doc = new Document();
        doc.setFileName(fileName);
        doc.setFileType(ext);
        doc.setFileSize(file.getSize());
        doc.setTenantId(tenantId);
        doc.setStatus("PENDING");
        documentMapper.insert(doc);

        try {
            doc.setStatus("PARSING");
            documentMapper.updateById(doc);

            DocumentParser parser = findParser(ext);
            if (parser == null) {
                throw new BusinessException(ErrorCode.FILE_TYPE_UNSUPPORTED, "不支持的文件类型: " + ext);
            }
            Map<String, String> parsed = parser.parse(file.getInputStream(), fileName);

            doc.setStatus("CHUNKING");
            documentMapper.updateById(doc);

            String text = parsed.get("text");
            String images = parsed.get("images");
            boolean needsVision = "true".equals(parsed.get("needsVision"));

            int chunkIndex = 0;

            if (text != null && !text.isBlank()) {
                List<String> textChunks = textChunker.chunk(text);
                for (String chunkContent : textChunks) {
                    Chunk chunk = new Chunk();
                    chunk.setDocumentId(doc.getId());
                    chunk.setContent(chunkContent);
                    chunk.setChunkType("text");
                    chunk.setChunkIndex(chunkIndex++);
                    chunkMapper.insert(chunk);
                }
            }

            if (images != null && !images.isBlank()) {
                String[] imagePathArray = images.split(";");
                for (String imgPath : imagePathArray) {
                    if (imgPath.isBlank()) continue;
                    Chunk chunk = new Chunk();
                    chunk.setDocumentId(doc.getId());
                    chunk.setContent(needsVision ? "[需要多模态处理]" : "");
                    chunk.setChunkType("image");
                    chunk.setImagePath(imgPath.trim());
                    chunk.setChunkIndex(chunkIndex);
                    chunkMapper.insert(chunk);
                    chunkIndex++;
                }
            }

            doc.setChunkCount(chunkIndex);
            doc.setStatus("DONE");
            documentMapper.updateById(doc);

            log.info("文档摄取完成: {}, 分片数: {}", fileName, chunkIndex);
            return doc;

        } catch (Exception e) {
            log.error("文档摄取失败: {}", fileName, e);
            doc.setStatus("FAILED");
            doc.setErrorMsg(e.getMessage());
            documentMapper.updateById(doc);
            throw e;
        }
    }

    private DocumentParser findParser(String ext) {
        if (ext == null) return null;
        String type = ext.toLowerCase();

        if (isImageType(type)) return findParserByType("image");

        return findParserByType(type);
    }

    private DocumentParser findParserByType(String type) {
        return parsers.stream()
                .filter(p -> p.supportedType().equalsIgnoreCase(type))
                .findFirst()
                .orElse(null);
    }

    private boolean isImageType(String ext) {
        for (String imgExt : com.agent.knowledge.parser.ImageParser.SUPPORTED_EXTENSIONS) {
            if (imgExt.equalsIgnoreCase(ext)) return true;
        }
        return false;
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "";
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }
}
