package com.agent.knowledge.controller;

import com.agent.common.Result;
import com.agent.knowledge.entity.Document;
import com.agent.knowledge.service.DocIngestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

// 知识库REST接口
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    @Autowired
    private DocIngestService docIngestService;

    // 上传文档
    @PostMapping("/documents/upload")
    public Result uploadDocument(@RequestParam("file") MultipartFile file,
                                  @RequestHeader("X-Tenant-Id") Long tenantId) throws Exception {
        Document doc = docIngestService.ingest(file, tenantId);
        return Result.ok(doc);
    }
}
