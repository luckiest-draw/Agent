package com.agent.workflow.entity;

import com.agent.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("wf_workflow_node")
public class WorkflowNode extends BaseEntity {
    private Long workflowId;
    private String nodeType;
    private String label;
    private Long agentConfigId;
    private Long toolDefId;
    private String position;
    private String nodeConfig;
}
