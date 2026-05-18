package com.agent.auth.entity;

import com.agent.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("sys_permission")
public class Permission extends BaseEntity {

    private String code;

    private String description;

    private String parentCode;
}
