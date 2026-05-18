package com.agent.tenant.controller;

import com.agent.common.Result;
import com.agent.tenant.entity.Tenant;
import com.agent.tenant.entity.TenantQuota;
import com.agent.tenant.service.TenantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    @Autowired
    private TenantService tenantService;

    @GetMapping
    public Result<List<Tenant>> list() {
        return Result.ok(tenantService.listAll());
    }

    @PostMapping
    public Result<Tenant> create(@RequestBody Tenant tenant) {
        return Result.ok(tenantService.create(tenant));
    }

    @PutMapping("/{id}")
    public Result<Tenant> update(@PathVariable Long id, @RequestBody Tenant tenant) {
        return Result.ok(tenantService.update(id, tenant));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        tenantService.delete(id);
        return Result.ok(); }

    @GetMapping("/{id}/quota")
    public Result<TenantQuota> getQuota(@PathVariable Long id) {
        return Result.ok(tenantService.getQuota(id));
    }

    @PutMapping("/{id}/quota")
    public Result<TenantQuota> updateQuota(@PathVariable Long id, @RequestBody TenantQuota quota) {
        return Result.ok(tenantService.updateQuota(id, quota));
    }
}
