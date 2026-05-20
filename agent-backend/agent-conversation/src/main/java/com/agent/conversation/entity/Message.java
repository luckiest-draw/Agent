package com.agent.conversation.entity;

import com.agent.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("conv_message")
public class Message extends BaseEntity {
    private Long conversationId;
    private String role;
    private String content;
    private String imageUrl;
    private Integer tokenCount;
    private Long responseTimeMs;
}
