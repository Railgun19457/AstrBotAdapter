package io.github.railgun19457.astrbotadapter.communication.auth;

/**
 * Token验证器
 */
public class TokenValidator {

    private final String validToken;

    public TokenValidator(String validToken) {
        this.validToken = validToken;
    }

    /**
     * 验证Token
     * @param token 待验证的Token
     * @return 验证结果
     */
    public AuthResult validate(String token) {
        if (token == null || token.isEmpty()) {
            return AuthResult.missingToken();
        }

        // 移除Bearer前缀
        String actualToken = token;
        if (token.startsWith("Bearer ")) {
            actualToken = token.substring(7);
        }

        if (validToken == null || validToken.isEmpty()) {
            return AuthResult.failure("服务器Token未配置", 1001);
        }

        if (validToken.equals(actualToken)) {
            return AuthResult.success();
        }

        return AuthResult.invalidToken();
    }

    /**
     * 验证Token是否有效
     * @param token 待验证的Token
     * @return 是否有效
     */
    public boolean isValid(String token) {
        return validate(token).isSuccess();
    }

    /**
     * 从Authorization头解析Token
     * @param authHeader Authorization头内容
     * @return Token字符串，无效时返回null
     */
    public static String parseFromHeader(String authHeader) {
        if (authHeader == null || authHeader.isEmpty()) {
            return null;
        }

        if (authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        return authHeader;
    }

    /**
     * 从URL参数解析Token
     * @param url URL字符串
     * @return Token字符串，无效时返回null
     */
    public static String parseFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        int tokenIndex = url.indexOf("token=");
        if (tokenIndex == -1) {
            return null;
        }

        int start = tokenIndex + 6;
        int end = url.indexOf("&", start);
        if (end == -1) {
            end = url.length();
        }

        return url.substring(start, end);
    }
}
