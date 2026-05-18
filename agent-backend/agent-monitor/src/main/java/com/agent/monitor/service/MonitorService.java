package com.agent.monitor.service;

import com.agent.monitor.entity.ApiLog;
import com.agent.monitor.entity.TokenUsage;
import java.util.List;

public interface MonitorService {
    List<ApiLog> listApiLogs(Long tenantId);
    ApiLog saveApiLog(ApiLog log);
    List<TokenUsage> listTokenUsage(Long tenantId, String date);
    TokenUsage saveTokenUsage(TokenUsage usage);
}
