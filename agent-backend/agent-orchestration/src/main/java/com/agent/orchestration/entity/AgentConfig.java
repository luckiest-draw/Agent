package com.agent.orchestration.entity;

import com.agent.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("orch_agent_config")
public class AgentConfig extends BaseEntity {
    private String name;
    private String description;
    private String model;
    private String systemPrompt;
    private Double temperature = 0.7;
    private Integer maxTokens = 2048;
    private Long promptTemplateId;
    private Long tenantId;
    private Boolean enabled = true;
}
