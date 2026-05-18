package com.agent.common;

// 统一业务错误码枚举
public enum ErrorCode {

    // 通用
    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "认证失败"),
    FORBIDDEN(403, "无访问权限"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "数据冲突"),
    INTERNAL_ERROR(500, "系统内部错误"),

    // 认证
    BAD_CREDENTIALS(401, "用户名或密码错误"),
    ACCOUNT_DISABLED(403, "账号已被禁用"),

    // 注册
    USERNAME_BLANK(400, "用户名不能为空"),
    PASSWORD_TOO_SHORT(400, "密码至少6位"),
    USERNAME_EXISTS(409, "用户名已存在"),

    // 文件
    FILE_TYPE_UNSUPPORTED(400, "不支持的文件类型"),
    FILE_UPLOAD_FAILED(500, "文件上传失败"),

    // 租户
    TENANT_NOT_FOUND(404, "租户不存在"),
    TENANT_DISABLED(403, "租户已禁用"),
    ;

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
