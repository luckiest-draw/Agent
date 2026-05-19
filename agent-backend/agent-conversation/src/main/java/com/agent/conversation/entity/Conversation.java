package com.agent.conversation.entity;

import com.agent.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("conv_conversation")
public class Conversation extends BaseEntity {
    private String title;
    private Long userId;
    private Long tenantId;
    private Long agentConfigId;
    private Integer messageCount = 0;
    private Long parentId;
    private String summary;
}
