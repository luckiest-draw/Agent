package com.agent.auth.entity;

import com.agent.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@TableName("sys_role")
public class Role extends BaseEntity {

    private String name;

    private String description;

    @TableField(exist = false)
    private Set<Permission> permissions = new HashSet<>();
}
