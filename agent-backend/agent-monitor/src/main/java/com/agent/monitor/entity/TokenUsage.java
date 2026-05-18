package com.agent.monitor.entity;

import com.agent.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("mon_token_usage")
public class TokenUsage extends BaseEntity {
    private Long tenantId;
    private Long userId;
    private String model;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private String usageDate;
}
