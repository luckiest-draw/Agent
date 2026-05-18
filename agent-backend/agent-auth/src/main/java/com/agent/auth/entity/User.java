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
@TableName("sys_user")
public class User extends BaseEntity {

    private String username;

    private String password;

    private String email;

    private String avatar;

    private Boolean enabled = true;

    @TableField(exist = false)
    private Set<Role> roles = new HashSet<>();
}
