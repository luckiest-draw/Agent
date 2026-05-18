package com.agent.knowledge.entity;

import com.agent.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("kb_document")
public class Document extends BaseEntity {
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String storagePath;
    private String status;
    private Integer chunkCount = 0;
    private Long tenantId;
    private String errorMsg;
}
