package com.agent.workflow.entity;

import com.agent.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("wf_workflow_edge")
public class WorkflowEdge extends BaseEntity {
    private Long workflowId;
    private Long sourceNodeId;
    private Long targetNodeId;
    private String label;
    private String condition;
}
