package io.github.railgun19457.astrbotadapter.communication.protocol;

/**
 * 错误码定义
 */
public enum ErrorCode {
    
    // 成功
    SUCCESS(0, "成功"),
    
    // 认证错误 (1xxx)
    AUTH_TOKEN_INVALID(1001, "认证失败：Token无效"),
    AUTH_TOKEN_EXPIRED(1002, "认证失败：Token过期"),
    AUTH_TOKEN_MISSING(1003, "认证失败：缺少Token"),
    
    // 请求错误 (2xxx)
    REQUEST_PARAM_ERROR(2001, "请求参数错误"),
    REQUEST_FORMAT_ERROR(2002, "请求格式错误"),
    REQUEST_PARAM_MISSING(2003, "缺少必要参数"),
    
    // 服务器错误 (3xxx)
    SERVER_INTERNAL_ERROR(3001, "服务器内部错误"),
    SERVER_UNAVAILABLE(3002, "服务不可用"),
    
    // 资源错误 (4xxx)
    RESOURCE_NOT_FOUND(4001, "资源不存在"),
    PLAYER_NOT_ONLINE(4002, "玩家不在线"),
    FEATURE_DISABLED(4003, "功能未启用"),
    
    // 指令错误 (5xxx)
    COMMAND_EXECUTE_FAILED(5001, "指令执行失败"),
    COMMAND_FILTERED(5002, "指令被过滤"),
    COMMAND_NO_PERMISSION(5003, "无执行权限");

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

    /**
     * 根据错误码获取枚举
     */
    public static ErrorCode fromCode(int code) {
        for (ErrorCode ec : values()) {
            if (ec.code == code) {
                return ec;
            }
        }
        return SERVER_INTERNAL_ERROR;
    }

    /**
     * 是否为成功状态
     */
    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
