package com.agent.knowledge.service;

import com.agent.knowledge.entity.Document;
import org.springframework.web.multipart.MultipartFile;

public interface DocIngestService {
    Document ingest(MultipartFile file, Long tenantId) throws Exception;
}
