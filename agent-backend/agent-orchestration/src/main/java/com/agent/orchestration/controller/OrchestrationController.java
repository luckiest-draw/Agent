package com.agent.orchestration.controller;

import com.agent.common.Result;
import com.agent.orchestration.entity.AgentConfig;
import com.agent.orchestration.entity.PromptTemplate;
import com.agent.orchestration.entity.Skill;
import com.agent.orchestration.entity.ToolDef;
import com.agent.orchestration.service.OrchestrationService;
import com.agent.orchestration.service.SkillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

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

    // Skill
    @Autowired
    private SkillService skillService;

    @GetMapping("/skills")
    public Result<List<Skill>> listSkills(@RequestHeader("X-Tenant-Id") Long tenantId) {
        return Result.ok(skillService.listSkills(tenantId));
    }

    @PostMapping("/skills")
    public Result<Skill> createSkill(@RequestBody Skill skill) {
        return Result.ok(skillService.createSkill(skill));
    }

    @PutMapping("/skills/{id}")
    public Result<Skill> updateSkill(@PathVariable Long id, @RequestBody Skill skill) {
        Skill updated = skillService.updateSkill(id, skill);
        return updated != null ? Result.ok(updated) : Result.fail(404, "Skill not found");
    }

    @DeleteMapping("/skills/{id}")
    public Result<Void> deleteSkill(@PathVariable Long id) {
        skillService.deleteSkill(id);
        return Result.ok();
    }

    @PostMapping("/skills/{id}/tools")
    public Result<Void> saveSkillTools(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        skillService.saveSkillTools(id, body.getOrDefault("toolIds", List.of()));
        return Result.ok();
    }
}
