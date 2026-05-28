package com.agent.orchestration.entity;

import com.agent.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("orch_skill")
public class Skill extends BaseEntity {
    private String name;
    private String description;
    private Long agentConfigId;
    private Long workflowId;
    private Long tenantId;
    private Boolean enabled = true;
}
