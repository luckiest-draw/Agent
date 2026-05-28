package com.agent.orchestration.service;

import com.agent.orchestration.entity.Skill;
import com.agent.orchestration.entity.SkillTool;
import com.agent.orchestration.entity.AgentConfig;
import com.agent.orchestration.entity.ToolDef;
import com.agent.orchestration.mapper.SkillMapper;
import com.agent.orchestration.mapper.SkillToolMapper;
import com.agent.orchestration.mapper.AgentConfigMapper;
import com.agent.orchestration.mapper.ToolDefMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SkillService {

    private final SkillMapper skillMapper;
    private final SkillToolMapper skillToolMapper;
    private final AgentConfigMapper agentConfigMapper;
    private final ToolDefMapper toolDefMapper;

    public SkillService(SkillMapper skillMapper, SkillToolMapper skillToolMapper,
                        AgentConfigMapper agentConfigMapper, ToolDefMapper toolDefMapper) {
        this.skillMapper = skillMapper;
        this.skillToolMapper = skillToolMapper;
        this.agentConfigMapper = agentConfigMapper;
        this.toolDefMapper = toolDefMapper;
    }

    public record SkillContext(Skill skill, AgentConfig agentConfig, List<ToolDef> tools) {}

    public SkillContext loadSkill(Long skillId) {
        Skill skill = skillMapper.selectById(skillId);
        if (skill == null) return null;

        AgentConfig agentConfig = agentConfigMapper.selectById(skill.getAgentConfigId());
        List<SkillTool> stList = skillToolMapper.selectList(
            new LambdaQueryWrapper<SkillTool>().eq(SkillTool::getSkillId, skillId));
        List<ToolDef> tools = stList.stream()
            .map(st -> toolDefMapper.selectById(st.getToolDefId()))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        return new SkillContext(skill, agentConfig, tools);
    }

    public List<Skill> listSkills(Long tenantId) {
        return skillMapper.selectList(
            new LambdaQueryWrapper<Skill>().eq(Skill::getTenantId, tenantId));
    }

    public List<Map<String, String>> listSkillsForRouting(Long tenantId) {
        return listSkills(tenantId).stream()
            .filter(s -> s.getEnabled() != null && s.getEnabled())
            .map(s -> Map.of("name", s.getName(), "description",
                s.getDescription() != null ? s.getDescription() : ""))
            .collect(Collectors.toList());
    }

    public Skill createSkill(Skill skill) {
        skillMapper.insert(skill);
        return skill;
    }

    public Skill updateSkill(Long id, Skill skill) {
        Skill existing = skillMapper.selectById(id);
        if (existing == null) return null;
        existing.setName(skill.getName());
        existing.setDescription(skill.getDescription());
        existing.setAgentConfigId(skill.getAgentConfigId());
        existing.setWorkflowId(skill.getWorkflowId());
        existing.setEnabled(skill.getEnabled());
        skillMapper.updateById(existing);
        return existing;
    }

    public void deleteSkill(Long id) {
        skillToolMapper.delete(new LambdaQueryWrapper<SkillTool>().eq(SkillTool::getSkillId, id));
        skillMapper.deleteById(id);
    }

    public void saveSkillTools(Long skillId, List<Long> toolDefIds) {
        skillToolMapper.delete(new LambdaQueryWrapper<SkillTool>().eq(SkillTool::getSkillId, skillId));
        for (Long toolId : toolDefIds) {
            SkillTool st = new SkillTool();
            st.setSkillId(skillId);
            st.setToolDefId(toolId);
            skillToolMapper.insert(st);
        }
    }
}
