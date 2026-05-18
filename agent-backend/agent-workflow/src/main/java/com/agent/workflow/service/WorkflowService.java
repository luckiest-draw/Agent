package com.agent.workflow.service;

import com.agent.workflow.entity.WorkflowDef;
import com.agent.workflow.entity.WorkflowEdge;
import com.agent.workflow.entity.WorkflowNode;
import java.util.List;

public interface WorkflowService {
    List<WorkflowDef> listWorkflows(Long tenantId);
    WorkflowDef getWorkflow(Long id);
    WorkflowDef createWorkflow(WorkflowDef workflow);
    WorkflowDef updateWorkflow(Long id, WorkflowDef workflow);
    void deleteWorkflow(Long id);
    List<WorkflowNode> listNodes(Long workflowId);
    List<WorkflowEdge> listEdges(Long workflowId);
    void saveNodesAndEdges(Long workflowId, List<WorkflowNode> nodes, List<WorkflowEdge> edges);
}
