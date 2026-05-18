package com.agent.tenant.service.impl;

import com.agent.tenant.service.TenantService;

import com.agent.common.BusinessException;
import com.agent.common.ErrorCode;
import com.agent.tenant.entity.Tenant;
import com.agent.tenant.entity.TenantQuota;
import com.agent.tenant.mapper.TenantMapper;
import com.agent.tenant.mapper.TenantQuotaMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class TenantServiceImpl implements TenantService {

    @Autowired
    private TenantMapper tenantMapper;

    @Autowired
    private TenantQuotaMapper tenantQuotaMapper;

    @Override
    public List<Tenant> listAll() { return tenantMapper.selectList(null); }

    @Override
    @Transactional
    public Tenant create(Tenant tenant) {
        boolean exists = tenantMapper.exists(
            new LambdaQueryWrapper<Tenant>().eq(Tenant::getName, tenant.getName()));
        if (exists)
            throw new BusinessException(ErrorCode.CONFLICT, "租户名称已存在");
        tenant.setApiKey("ak-" + UUID.randomUUID().toString().replace("-", ""));
        tenantMapper.insert(tenant);
        TenantQuota quota = new TenantQuota();
        quota.setTenantId(tenant.getId());
        tenantQuotaMapper.insert(quota);
        return tenant;
    }

    @Override
    @Transactional
    public Tenant update(Long id, Tenant updated) {
        Tenant existing = tenantMapper.selectById(id);
        if (existing == null)
            throw new BusinessException(ErrorCode.TENANT_NOT_FOUND);
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setEnabled(updated.getEnabled());
        tenantMapper.updateById(existing);
        return existing;
    }

    @Override
    public void delete(Long id) { tenantMapper.deleteById(id); }

    @Override
    public TenantQuota getQuota(Long tenantId) {
        return tenantQuotaMapper.selectOne(
            new LambdaQueryWrapper<TenantQuota>().eq(TenantQuota::getTenantId, tenantId));
    }

    @Override
    @Transactional
    public TenantQuota updateQuota(Long tenantId, TenantQuota quota) {
        TenantQuota existing = getQuota(tenantId);
        existing.setMaxDocuments(quota.getMaxDocuments());
        existing.setMaxTokensPerDay(quota.getMaxTokensPerDay());
        existing.setMaxConversations(quota.getMaxConversations());
        existing.setMaxAgents(quota.getMaxAgents());
        tenantQuotaMapper.updateById(existing);
        return existing;
    }
}
