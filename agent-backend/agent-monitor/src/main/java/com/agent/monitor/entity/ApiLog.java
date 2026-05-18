package com.agent.monitor.entity;

import com.agent.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("mon_api_log")
public class ApiLog extends BaseEntity {
    private Long tenantId;
    private Long userId;
    private String model;
    private String method;
    private String path;
    private Integer statusCode;
    private Long responseTimeMs;
    private String errorMsg;
}
