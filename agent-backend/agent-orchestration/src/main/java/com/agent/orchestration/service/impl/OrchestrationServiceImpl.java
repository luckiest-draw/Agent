package com.agent.orchestration.service.impl;

import com.agent.orchestration.service.OrchestrationService;

import com.agent.common.BusinessException;
import com.agent.common.ErrorCode;
import com.agent.orchestration.entity.AgentConfig;
import com.agent.orchestration.entity.PromptTemplate;
import com.agent.orchestration.entity.ToolDef;
import com.agent.orchestration.mapper.AgentConfigMapper;
import com.agent.orchestration.mapper.PromptTemplateMapper;
import com.agent.orchestration.mapper.ToolDefMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class OrchestrationServiceImpl implements OrchestrationService {

    @Autowired
    private AgentConfigMapper agentConfigMapper;

    @Autowired
    private PromptTemplateMapper promptTemplateMapper;

    @Autowired
    private ToolDefMapper toolDefMapper;

    @Override
    public List<AgentConfig> listAgentConfigs(Long tenantId) {
        return agentConfigMapper.selectList(
            new LambdaQueryWrapper<AgentConfig>().eq(AgentConfig::getTenantId, tenantId));
    }

    @Override
    public AgentConfig createAgentConfig(AgentConfig config) {
        agentConfigMapper.insert(config);
        return config;
    }

    @Override
    public AgentConfig updateAgentConfig(Long id, AgentConfig config) {
        AgentConfig existing = agentConfigMapper.selectById(id);
        if (existing == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "AgentConfig不存在");
        existing.setName(config.getName());
        existing.setDescription(config.getDescription());
        existing.setModel(config.getModel());
        existing.setSystemPrompt(config.getSystemPrompt());
        existing.setTemperature(config.getTemperature());
        existing.setMaxTokens(config.getMaxTokens());
        existing.setEnabled(config.getEnabled());
        agentConfigMapper.updateById(existing);
        return existing;
    }

    @Override
    public void deleteAgentConfig(Long id) {
        agentConfigMapper.deleteById(id);
    }

    @Override
    public List<PromptTemplate> listTemplates(Long tenantId) {
        return promptTemplateMapper.selectList(
            new LambdaQueryWrapper<PromptTemplate>().eq(PromptTemplate::getTenantId, tenantId));
    }

    @Override
    public PromptTemplate createTemplate(PromptTemplate template) {
        promptTemplateMapper.insert(template);
        return template;
    }

    @Override
    public PromptTemplate updateTemplate(Long id, PromptTemplate template) {
        PromptTemplate existing = promptTemplateMapper.selectById(id);
        if (existing == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "模板不存在");
        existing.setName(template.getName());
        existing.setTemplate(template.getTemplate());
        existing.setVariables(template.getVariables());
        promptTemplateMapper.updateById(existing);
        return existing;
    }

    @Override
    public void deleteTemplate(Long id) {
        promptTemplateMapper.deleteById(id);
    }

    @Override
    public List<ToolDef> listTools(Long tenantId) {
        return toolDefMapper.selectList(
            new LambdaQueryWrapper<ToolDef>().eq(ToolDef::getTenantId, tenantId));
    }

    @Override
    public ToolDef createTool(ToolDef tool) {
        toolDefMapper.insert(tool);
        return tool;
    }

    @Override
    public ToolDef updateTool(Long id, ToolDef tool) {
        ToolDef existing = toolDefMapper.selectById(id);
        if (existing == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "工具不存在");
        existing.setName(tool.getName());
        existing.setDescription(tool.getDescription());
        existing.setParameters(tool.getParameters());
        existing.setToolType(tool.getToolType());
        existing.setEndpoint(tool.getEndpoint());
        existing.setEnabled(tool.getEnabled());
        toolDefMapper.updateById(existing);
        return existing;
    }

    @Override
    public void deleteTool(Long id) {
        toolDefMapper.deleteById(id);
    }
}
