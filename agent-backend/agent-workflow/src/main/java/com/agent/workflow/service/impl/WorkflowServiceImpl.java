package com.agent.workflow.service.impl;

import com.agent.workflow.service.WorkflowService;

import com.agent.common.BusinessException;
import com.agent.common.ErrorCode;
import com.agent.workflow.entity.WorkflowDef;
import com.agent.workflow.entity.WorkflowEdge;
import com.agent.workflow.entity.WorkflowNode;
import com.agent.workflow.mapper.WorkflowDefMapper;
import com.agent.workflow.mapper.WorkflowEdgeMapper;
import com.agent.workflow.mapper.WorkflowNodeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class WorkflowServiceImpl implements WorkflowService {

    @Autowired
    private WorkflowDefMapper workflowDefMapper;

    @Autowired
    private WorkflowNodeMapper workflowNodeMapper;

    @Autowired
    private WorkflowEdgeMapper workflowEdgeMapper;

    @Override
    public List<WorkflowDef> listWorkflows(Long tenantId) {
        return workflowDefMapper.selectList(
            new LambdaQueryWrapper<WorkflowDef>().eq(WorkflowDef::getTenantId, tenantId));
    }

    @Override
    public WorkflowDef getWorkflow(Long id) {
        WorkflowDef wf = workflowDefMapper.selectById(id);
        if (wf == null) throw new BusinessException(ErrorCode.NOT_FOUND, "工作流不存在");
        return wf;
    }

    @Override
    public WorkflowDef createWorkflow(WorkflowDef workflow) {
        workflowDefMapper.insert(workflow);
        return workflow;
    }

    @Override
    public WorkflowDef updateWorkflow(Long id, WorkflowDef workflow) {
        WorkflowDef existing = workflowDefMapper.selectById(id);
        if (existing == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "工作流不存在");
        existing.setName(workflow.getName());
        existing.setDescription(workflow.getDescription());
        existing.setStatus(workflow.getStatus());
        workflowDefMapper.updateById(existing);
        return existing;
    }

    @Override
    public void deleteWorkflow(Long id) {
        workflowDefMapper.deleteById(id);
    }

    @Override
    public List<WorkflowNode> listNodes(Long workflowId) {
        return workflowNodeMapper.selectList(
            new LambdaQueryWrapper<WorkflowNode>()
                .eq(WorkflowNode::getWorkflowId, workflowId)
                .orderByAsc(WorkflowNode::getId));
    }

    @Override
    @Transactional
    public void saveNodesAndEdges(Long workflowId, List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        workflowEdgeMapper.delete(
            new LambdaQueryWrapper<WorkflowEdge>().eq(WorkflowEdge::getWorkflowId, workflowId));
        workflowNodeMapper.delete(
            new LambdaQueryWrapper<WorkflowNode>().eq(WorkflowNode::getWorkflowId, workflowId));

        for (WorkflowNode node : nodes) {
            node.setWorkflowId(workflowId);
            workflowNodeMapper.insert(node);
        }

        for (WorkflowEdge edge : edges) {
            edge.setWorkflowId(workflowId);
            workflowEdgeMapper.insert(edge);
        }
    }

    @Override
    public List<WorkflowEdge> listEdges(Long workflowId) {
        return workflowEdgeMapper.selectList(
            new LambdaQueryWrapper<WorkflowEdge>()
                .eq(WorkflowEdge::getWorkflowId, workflowId)
                .orderByAsc(WorkflowEdge::getId));
    }
}
