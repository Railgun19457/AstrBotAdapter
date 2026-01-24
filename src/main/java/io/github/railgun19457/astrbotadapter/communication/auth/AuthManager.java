package io.github.railgun19457.astrbotadapter.communication.auth;

import io.github.railgun19457.astrbotadapter.core.config.ConfigManager;
import io.github.railgun19457.astrbotadapter.core.config.PluginConfig;
import io.github.railgun19457.astrbotadapter.core.util.TokenGenerator;

import java.util.logging.Logger;

/**
 * 认证管理器
 * 负责Token的生成、验证和管理
 */
public class AuthManager {

    private final ConfigManager configManager;
    private final Logger logger;
    private TokenValidator tokenValidator;

    public AuthManager(ConfigManager configManager, Logger logger) {
        this.configManager = configManager;
        this.logger = logger;
    }

    /**
     * 初始化认证管理器
     * 如果配置中没有Token，则生成新的Token
     */
    public void initialize() {
        PluginConfig config = configManager.getConfig();
        String token = config.getToken();

        if (token == null || token.isEmpty()) {
            // 生成新Token
            token = TokenGenerator.generate(32);
            configManager.updateToken(token);
            logger.info("已生成新的认证Token");
            logger.info("Token: " + token);
            logger.warning("请妥善保管此Token，不要泄露给他人！");
        }

        this.tokenValidator = new TokenValidator(token);
        logger.info("认证管理器已初始化");
    }

    /**
     * 重新加载认证配置
     */
    public void reload() {
        PluginConfig config = configManager.getConfig();
        String token = config.getToken();

        if (token == null || token.isEmpty()) {
            logger.warning("Token为空，将生成新的Token");
            token = TokenGenerator.generate(32);
            configManager.updateToken(token);
        }

        this.tokenValidator = new TokenValidator(token);
        logger.info("认证管理器已重载");
    }

    /**
     * 验证Token
     * @param token 待验证的Token
     * @return 验证结果
     */
    public AuthResult authenticate(String token) {
        if (tokenValidator == null) {
            return AuthResult.failure("认证管理器未初始化", 3001);
        }
        return tokenValidator.validate(token);
    }

    /**
     * 验证Authorization头
     * @param authHeader Authorization头内容
     * @return 验证结果
     */
    public AuthResult authenticateHeader(String authHeader) {
        String token = TokenValidator.parseFromHeader(authHeader);
        return authenticate(token);
    }

    /**
     * 验证URL中的Token
     * @param url URL字符串
     * @return 验证结果
     */
    public AuthResult authenticateUrl(String url) {
        String token = TokenValidator.parseFromUrl(url);
        return authenticate(token);
    }

    /**
     * 生成新的Token并保存
     * @return 新生成的Token
     */
    public String regenerateToken() {
        String newToken = TokenGenerator.generate(32);
        configManager.updateToken(newToken);
        this.tokenValidator = new TokenValidator(newToken);
        logger.info("已生成新的认证Token");
        return newToken;
    }

    /**
     * 获取当前Token
     * @return 当前Token
     */
    public String getCurrentToken() {
        return configManager.getConfig().getToken();
    }

    /**
     * 获取Token验证器
     */
    public TokenValidator getTokenValidator() {
        return tokenValidator;
    }
}
