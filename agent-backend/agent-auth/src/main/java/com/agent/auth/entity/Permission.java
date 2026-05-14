package com.agent.auth.entity;

import com.agent.common.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "sys_permission")
public class Permission extends BaseEntity {

    @Column(unique = true, nullable = false, length = 50)
    private String code;

    @Column(length = 100)
    private String description;

    @Column(length = 50)
    private String parentCode;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getParentCode() {
        return parentCode;
    }

    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
    }
}
