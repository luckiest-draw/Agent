package com.agent.workflow.controller;

import com.agent.common.Result;
import com.agent.workflow.entity.WorkflowDef;
import com.agent.workflow.entity.WorkflowEdge;
import com.agent.workflow.entity.WorkflowNode;
import com.agent.workflow.service.WorkflowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import lombok.Data;

import java.util.Map;

@Data
class DagData {
    private List<WorkflowNode> nodes;
    private List<WorkflowEdge> edges;
}

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    @Autowired
    private WorkflowService workflowService;

    @GetMapping
    public Result<List<WorkflowDef>> list(@RequestHeader("X-Tenant-Id") Long tenantId) {
        return Result.ok(workflowService.listWorkflows(tenantId));
    }

    @GetMapping("/{id}")
    public Result<WorkflowDef> get(@PathVariable Long id) {
        return Result.ok(workflowService.getWorkflow(id));
    }

    @PostMapping
    public Result<WorkflowDef> create(@RequestBody WorkflowDef workflow) {
        return Result.ok(workflowService.createWorkflow(workflow));
    }

    @PutMapping("/{id}")
    public Result<WorkflowDef> update(@PathVariable Long id, @RequestBody WorkflowDef workflow) {
        return Result.ok(workflowService.updateWorkflow(id, workflow));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        workflowService.deleteWorkflow(id);
        return Result.ok();
    }

    // DAG节点与边
    @GetMapping("/{id}/dag")
    public Result<Map<String, Object>> getDag(@PathVariable Long id) {
        List<WorkflowNode> nodes = workflowService.listNodes(id);
        List<WorkflowEdge> edges = workflowService.listEdges(id);
        return Result.ok(Map.of("nodes", nodes, "edges", edges));
    }

    @PutMapping("/{id}/dag")
    public Result<Void> saveDag(@PathVariable Long id, @RequestBody DagData dagData) {
        workflowService.saveNodesAndEdges(id, dagData.getNodes(), dagData.getEdges());
        return Result.ok();
    }
}
