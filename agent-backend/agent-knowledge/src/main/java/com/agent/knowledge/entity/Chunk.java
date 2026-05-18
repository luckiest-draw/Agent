package com.agent.knowledge.entity;

import com.agent.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("kb_chunk")
public class Chunk extends BaseEntity {
    private Long documentId;
    private String content;
    private String chunkType;
    private String imagePath;
    private Integer chunkIndex;
    private Boolean vectorized = false;
}
