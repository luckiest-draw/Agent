package com.agent.orchestration.entity;

import com.agent.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("orch_skill_tool")
public class SkillTool extends BaseEntity {
    private Long skillId;
    private Long toolDefId;
}
