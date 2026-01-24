package io.github.railgun19457.astrbotadapter.core.config;

import java.util.logging.Logger;

/**
 * 配置验证器
 * 验证配置项的有效性并提供修复建议
 */
public class ConfigValidator {

    private final Logger logger;

    public ConfigValidator(Logger logger) {
        this.logger = logger;
    }

    /**
     * 验证配置
     * @param config 配置对象
     * @return 配置是否完全有效
     */
    public boolean validate(PluginConfig config) {
        boolean valid = true;

        // 验证语言设置
        String lang = config.getLanguage();
        if (lang == null || (!lang.equals("zh_CN") && !lang.equals("en_US"))) {
            logger.warning("无效的语言设置: " + lang + ", 已设为默认值 zh_CN");
            config.setLanguage("zh_CN");
            valid = false;
        }

        // 验证端口范围
        if (config.getWsPort() < 1 || config.getWsPort() > 65535) {
            logger.warning("WebSocket端口无效: " + config.getWsPort() + ", 已设为默认值 8765");
            config.setWsPort(8765);
            valid = false;
        }

        if (config.getRestPort() < 1 || config.getRestPort() > 65535) {
            logger.warning("REST API端口无效: " + config.getRestPort() + ", 已设为默认值 8766");
            config.setRestPort(8766);
            valid = false;
        }

        // 验证端口冲突
        if (config.isWsEnabled() && config.isRestEnabled() && 
            config.getWsPort() == config.getRestPort()) {
            logger.warning("WebSocket和REST API端口冲突，REST API端口已修改为 " + (config.getWsPort() + 1));
            config.setRestPort(config.getWsPort() + 1);
            valid = false;
        }

        // 验证心跳设置
        if (config.getHeartbeatInterval() < 5) {
            logger.warning("心跳间隔过短: " + config.getHeartbeatInterval() + "秒, 已设为最小值 5秒");
            config.setHeartbeatInterval(5);
            valid = false;
        }

        if (config.getHeartbeatTimeout() <= config.getHeartbeatInterval()) {
            int timeout = config.getHeartbeatInterval() * 3;
            logger.warning("心跳超时应大于心跳间隔, 已设为 " + timeout + "秒");
            config.setHeartbeatTimeout(timeout);
            valid = false;
        }

        // 验证前缀冲突
        String groupPrefix = config.getGroupChatPrefix();
        String privatePrefix = config.getPrivateChatPrefix();
        String forwardPrefix = config.getForwardPrefix();

        if (config.isGroupChatEnabled() && config.isPrivateChatEnabled()) {
            if (groupPrefix != null && groupPrefix.equals(privatePrefix) && !groupPrefix.isEmpty()) {
                logger.warning("群聊和私聊前缀相同: '" + groupPrefix + "', 将按群聊处理");
            }
        }

        // 验证指令过滤模式
        String filterMode = config.getCommandFilterMode();
        if (filterMode == null || 
            (!filterMode.equals("NONE") && !filterMode.equals("WHITELIST") && !filterMode.equals("BLACKLIST"))) {
            logger.warning("无效的指令过滤模式: " + filterMode + ", 已设为 NONE");
            config.setCommandFilterMode("NONE");
            valid = false;
        }

        // 验证频率限制
        if (config.getRateLimit() < 0) {
            logger.warning("无效的频率限制: " + config.getRateLimit() + ", 已设为 0（不限制）");
            config.setRateLimit(0);
            valid = false;
        }

        return valid;
    }

    /**
     * 验证Token格式
     * @param token Token字符串
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        
        // Token长度应在16-64之间
        if (token.length() < 16 || token.length() > 64) {
            return false;
        }

        // Token只能包含字母数字和部分特殊字符
        return token.matches("^[a-zA-Z0-9_-]+$");
    }
}
