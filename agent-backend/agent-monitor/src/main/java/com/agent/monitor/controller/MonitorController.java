package com.agent.monitor.controller;

import com.agent.common.Result;
import com.agent.monitor.entity.ApiLog;
import com.agent.monitor.entity.TokenUsage;
import com.agent.monitor.service.MonitorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    @Autowired
    private MonitorService monitorService;

    @GetMapping("/api-logs")
    public Result<List<ApiLog>> listApiLogs(@RequestHeader("X-Tenant-Id") Long tenantId) {
        return Result.ok(monitorService.listApiLogs(tenantId));
    }

    @GetMapping("/token-usage")
    public Result<List<TokenUsage>> listTokenUsage(@RequestHeader("X-Tenant-Id") Long tenantId,
                                                   @RequestParam String date) {
        return Result.ok(monitorService.listTokenUsage(tenantId, date));
    }
}
