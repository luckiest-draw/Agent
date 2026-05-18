package com.agent.orchestration.service;

import com.agent.orchestration.entity.AgentConfig;
import com.agent.orchestration.entity.PromptTemplate;
import com.agent.orchestration.entity.ToolDef;
import java.util.List;

public interface OrchestrationService {
    List<AgentConfig> listAgentConfigs(Long tenantId);
    AgentConfig createAgentConfig(AgentConfig config);
    AgentConfig updateAgentConfig(Long id, AgentConfig config);
    void deleteAgentConfig(Long id);

    List<PromptTemplate> listTemplates(Long tenantId);
    PromptTemplate createTemplate(PromptTemplate template);
    PromptTemplate updateTemplate(Long id, PromptTemplate template);
    void deleteTemplate(Long id);

    List<ToolDef> listTools(Long tenantId);
    ToolDef createTool(ToolDef tool);
    ToolDef updateTool(Long id, ToolDef tool);
    void deleteTool(Long id);
}
