package com.agent.workflow.entity;

import com.agent.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("wf_workflow_def")
public class WorkflowDef extends BaseEntity {
    private String name;
    private String description;
    private String status = "DRAFT";
    private Long tenantId;
}
