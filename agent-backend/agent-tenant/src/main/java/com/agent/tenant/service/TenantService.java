package com.agent.tenant.service;

import com.agent.tenant.entity.Tenant;
import com.agent.tenant.entity.TenantQuota;
import java.util.List;

public interface TenantService {
    List<Tenant> listAll();
    Tenant create(Tenant tenant);
    Tenant update(Long id, Tenant updated);
    void delete(Long id);
    TenantQuota getQuota(Long tenantId);
    TenantQuota updateQuota(Long tenantId, TenantQuota quota);
}
