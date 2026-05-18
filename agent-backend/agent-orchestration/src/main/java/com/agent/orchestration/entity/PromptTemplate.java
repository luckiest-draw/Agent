package com.agent.orchestration.entity;

import com.agent.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("orch_prompt_template")
public class PromptTemplate extends BaseEntity {
    private String name;
    private String template;
    private String variables;
    private Long tenantId;
}
