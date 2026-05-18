package com.agent.orchestration.controller;

import com.agent.common.Result;
import com.agent.orchestration.entity.AgentConfig;
import com.agent.orchestration.entity.PromptTemplate;
import com.agent.orchestration.entity.ToolDef;
import com.agent.orchestration.service.OrchestrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/orchestration")
public class OrchestrationController {

    @Autowired
    private OrchestrationService orchestrationService;

    // Agent Config
    @GetMapping("/agents")
    public Result<List<AgentConfig>> listAgents(@RequestHeader("X-Tenant-Id") Long tenantId) {
        return Result.ok(orchestrationService.listAgentConfigs(tenantId));
    }

    @PostMapping("/agents")
    public Result<AgentConfig> createAgent(@RequestBody AgentConfig config) {
        return Result.ok(orchestrationService.createAgentConfig(config));
    }

    @PutMapping("/agents/{id}")
    public Result<AgentConfig> updateAgent(@PathVariable Long id, @RequestBody AgentConfig config) {
        return Result.ok(orchestrationService.updateAgentConfig(id, config));
    }

    @DeleteMapping("/agents/{id}")
    public Result<Void> deleteAgent(@PathVariable Long id) {
        orchestrationService.deleteAgentConfig(id);
        return Result.ok();
    }

    // Prompt Template
    @GetMapping("/templates")
    public Result<List<PromptTemplate>> listTemplates(@RequestHeader("X-Tenant-Id") Long tenantId) {
        return Result.ok(orchestrationService.listTemplates(tenantId));
    }

    @PostMapping("/templates")
    public Result<PromptTemplate> createTemplate(@RequestBody PromptTemplate template) {
        return Result.ok(orchestrationService.createTemplate(template));
    }

    @PutMapping("/templates/{id}")
    public Result<PromptTemplate> updateTemplate(@PathVariable Long id, @RequestBody PromptTemplate template) {
        return Result.ok(orchestrationService.updateTemplate(id, template));
    }

    @DeleteMapping("/templates/{id}")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        orchestrationService.deleteTemplate(id);
        return Result.ok();
    }

    // Tool Def
    @GetMapping("/tools")
    public Result<List<ToolDef>> listTools(@RequestHeader("X-Tenant-Id") Long tenantId) {
        return Result.ok(orchestrationService.listTools(tenantId));
    }

    @PostMapping("/tools")
    public Result<ToolDef> createTool(@RequestBody ToolDef tool) {
        return Result.ok(orchestrationService.createTool(tool));
    }

    @PutMapping("/tools/{id}")
    public Result<ToolDef> updateTool(@PathVariable Long id, @RequestBody ToolDef tool) {
        return Result.ok(orchestrationService.updateTool(id, tool));
    }

    @DeleteMapping("/tools/{id}")
    public Result<Void> deleteTool(@PathVariable Long id) {
        orchestrationService.deleteTool(id);
        return Result.ok();
    }
}
