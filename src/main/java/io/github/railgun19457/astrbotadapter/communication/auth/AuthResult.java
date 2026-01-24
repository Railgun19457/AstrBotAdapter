package io.github.railgun19457.astrbotadapter.communication.auth;

/**
 * 认证结果
 */
public class AuthResult {

    private final boolean success;
    private final String message;
    private final int errorCode;

    private AuthResult(boolean success, String message, int errorCode) {
        this.success = success;
        this.message = message;
        this.errorCode = errorCode;
    }

    /**
     * 创建成功结果
     */
    public static AuthResult success() {
        return new AuthResult(true, "认证成功", 0);
    }

    /**
     * 创建失败结果
     */
    public static AuthResult failure(String message, int errorCode) {
        return new AuthResult(false, message, errorCode);
    }

    /**
     * Token无效
     */
    public static AuthResult invalidToken() {
        return new AuthResult(false, "Token无效", 1001);
    }

    /**
     * Token过期
     */
    public static AuthResult expiredToken() {
        return new AuthResult(false, "Token过期", 1002);
    }

    /**
     * 缺少Token
     */
    public static AuthResult missingToken() {
        return new AuthResult(false, "缺少Token", 1003);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
