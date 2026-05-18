package com.agent.orchestration.entity;

import com.agent.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("orch_tool_def")
public class ToolDef extends BaseEntity {
    private String name;
    private String description;
    private String parameters;
    private String toolType;
    private String endpoint;
    private Long tenantId;
    private Boolean enabled = true;
}
