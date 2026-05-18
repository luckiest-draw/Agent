package com.agent.tenant.entity;

import com.agent.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("sys_tenant")
public class Tenant extends BaseEntity {

    private String name;

    private String description;

    private Boolean enabled = true;

    private String apiKey;
}
