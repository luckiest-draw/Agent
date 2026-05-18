package com.agent.monitor.service.impl;

import com.agent.monitor.service.MonitorService;

import com.agent.monitor.entity.ApiLog;
import com.agent.monitor.entity.TokenUsage;
import com.agent.monitor.mapper.ApiLogMapper;
import com.agent.monitor.mapper.TokenUsageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class MonitorServiceImpl implements MonitorService {

    @Autowired
    private ApiLogMapper apiLogMapper;

    @Autowired
    private TokenUsageMapper tokenUsageMapper;

    @Override
    public List<ApiLog> listApiLogs(Long tenantId) {
        return apiLogMapper.selectList(
            new LambdaQueryWrapper<ApiLog>()
                .eq(ApiLog::getTenantId, tenantId)
                .orderByDesc(ApiLog::getCreatedAt));
    }

    @Override
    public ApiLog saveApiLog(ApiLog log) {
        apiLogMapper.insert(log);
        return log;
    }

    @Override
    public List<TokenUsage> listTokenUsage(Long tenantId, String date) {
        return tokenUsageMapper.selectList(
            new LambdaQueryWrapper<TokenUsage>()
                .eq(TokenUsage::getTenantId, tenantId)
                .eq(TokenUsage::getUsageDate, date)
                .orderByDesc(TokenUsage::getCreatedAt));
    }

    @Override
    public TokenUsage saveTokenUsage(TokenUsage usage) {
        tokenUsageMapper.insert(usage);
        return usage;
    }
}
