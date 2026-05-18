package com.agent.tenant.entity;

import com.agent.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("sys_tenant_quota")
public class TenantQuota extends BaseEntity {
    private Long tenantId;

    private Long maxDocuments = 1000L;
    private Long maxTokensPerDay = 100000L;
    private Long maxConversations = 100L;
    private Long maxAgents = 10L;
}
