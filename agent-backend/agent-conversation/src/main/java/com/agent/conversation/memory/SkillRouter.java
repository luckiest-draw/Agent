package com.agent.conversation.memory;

import com.agent.conversation.client.SkillRouteClient;
import com.agent.orchestration.entity.Skill;
import com.agent.orchestration.entity.AgentConfig;
import com.agent.orchestration.entity.ToolDef;
import com.agent.orchestration.service.SkillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SkillRouter {
    private static final Logger log = LoggerFactory.getLogger(SkillRouter.class);

    private final SkillService skillService;
    private final SkillRouteClient routeClient;

    public SkillRouter(SkillService skillService, SkillRouteClient routeClient) {
        this.skillService = skillService;
        this.routeClient = routeClient;
    }

    public record RouteResult(SkillService.SkillContext skillContext,
                              String systemPrompt, List<String> toolNames, String skillName) {}

    /** 根据用户消息匹配技能，返回 null 表示走通用对话 */
    public RouteResult route(String userMessage) {
        List<Map<String, String>> skills = skillService.listSkillsForRouting(null);
        if (skills.isEmpty()) {
            log.debug("No skills available for routing");
            return null;
        }

        SkillRouteClient.RouteResult match = routeClient.route(userMessage, skills);
        if (!match.matched()) {
            log.debug("No skill matched for: {}", userMessage.substring(0, Math.min(50, userMessage.length())));
            return null;
        }

        // 按名称查找匹配的 Skill
        Skill matchedSkill = skillService.listSkills(null).stream()
            .filter(s -> s.getName().equals(match.skillName()))
            .findFirst()
            .orElse(null);
        if (matchedSkill == null) return null;

        SkillService.SkillContext ctx = skillService.loadSkill(matchedSkill.getId());
        if (ctx == null || ctx.agentConfig() == null) return null;

        String systemPrompt = ctx.agentConfig().getSystemPrompt();
        List<String> toolNames = ctx.tools().stream()
            .map(ToolDef::getName)
            .collect(Collectors.toList());

        log.info("SkillRouter: matched '{}' (score={:.3f}), tools={}",
            match.skillName(), match.score(), toolNames);

        return new RouteResult(ctx, systemPrompt, toolNames, match.skillName());
    }

    /** 根据 skillId 直接加载技能上下文（已知道要用哪个技能时使用） */
    public RouteResult loadById(Long skillId) {
        if (skillId == null) return null;
        SkillService.SkillContext ctx = skillService.loadSkill(skillId);
        if (ctx == null || ctx.agentConfig() == null) return null;

        List<String> toolNames = ctx.tools().stream()
            .map(ToolDef::getName)
            .collect(Collectors.toList());

        return new RouteResult(ctx, ctx.agentConfig().getSystemPrompt(),
            toolNames, ctx.skill().getName());
    }
}
