package io.github.railgun19457.astrbotadapter.core.config;

/**
 * 插件配置数据类
 * 对应 config.yml 中的所有配置项
 */
public class PluginConfig {

    // 基础设置
    private String language = "zh_CN";
    private boolean debug = false;

    // 认证设置
    private String token = "";

    // 统一服务器配置（新）
    private String serverHost = "0.0.0.0";
    private int serverPort = 8765;

    // WebSocket 服务器配置
    private boolean wsEnabled = true;
    private String wsHost = "0.0.0.0";  // 保留用于兼容旧配置
    private int wsPort = 8765;           // 保留用于兼容旧配置
    private int heartbeatInterval = 30;
    private int heartbeatTimeout = 90;

    // REST API 服务器配置
    private boolean restEnabled = true;
    private String restHost = "0.0.0.0"; // 保留用于兼容旧配置
    private int restPort = 8766;          // 保留用于兼容旧配置
    private int rateLimit = 100;

    // 消息转发配置
    private boolean forwardEnabled = true;
    private String forwardPrefix = "*";
    private boolean stripPrefix = true;
    private String incomingFormat = "§7[§b{platform}§7] §f{username}§7: §f{content}";

    // AI 群聊配置
    private boolean groupChatEnabled = true;
    private String groupChatPrefix = "@";

    // AI 私聊配置
    private boolean privateChatEnabled = true;
    private String privateChatPrefix = "#";
    private String privateChatEchoFormat = "<{player}> {message}";

    // AI 回复格式
    private String aiResponseFormat = "§7[§dAI§7] §f{content}";
    private String aiThinkingMessage = "§7[§dAI§7] §e思考中...";
    private boolean aiShowThinking = true;
    private int aiTimeoutSeconds = 60;

    // 玩家通知配置
    private boolean joinNotifyEnabled = true;
    private boolean quitNotifyEnabled = true;

    // 外部指令配置
    private boolean commandEnabled = true;
    private String commandFilterMode = "NONE"; // NONE, WHITELIST, BLACKLIST
    private java.util.List<String> commandFilterList = new java.util.ArrayList<>();

    // 日志查询
    private boolean logQueryEnabled = true;
    private int logQueryMaxLines = 1000;
    private String logQueryFile = "";

    // 代理模式配置 (后端服务器)
    private boolean proxyModeEnabled = false;  // Whether this backend works in proxy mode
    private String proxySecret = "";           // Secret for authenticating with proxy

    // 代理桥接配置 (Velocity代理端)
    private boolean proxyBridgeEnabled = false; // Whether proxy bridge is enabled on Velocity

    // Config sync metadata (backend)
    private long syncedConfigVersion = 0L;
    private String syncedConfigHash = "";
    private long syncedConfigUpdatedAt = 0L;

    // 更新检查
    private boolean updateCheckEnabled = true;
    private boolean updateNotifyOps = true;

    // Getters and Setters

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getServerHost() {
        return serverHost;
    }

    public void setServerHost(String serverHost) {
        this.serverHost = serverHost;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public boolean isWsEnabled() {
        return wsEnabled;
    }

    public void setWsEnabled(boolean wsEnabled) {
        this.wsEnabled = wsEnabled;
    }

    public String getWsHost() {
        return wsHost;
    }

    public void setWsHost(String wsHost) {
        this.wsHost = wsHost;
    }

    public int getWsPort() {
        return wsPort;
    }

    public void setWsPort(int wsPort) {
        this.wsPort = wsPort;
    }

    public int getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(int heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public int getHeartbeatTimeout() {
        return heartbeatTimeout;
    }

    public void setHeartbeatTimeout(int heartbeatTimeout) {
        this.heartbeatTimeout = heartbeatTimeout;
    }

    public boolean isRestEnabled() {
        return restEnabled;
    }

    public void setRestEnabled(boolean restEnabled) {
        this.restEnabled = restEnabled;
    }

    public String getRestHost() {
        return restHost;
    }

    public void setRestHost(String restHost) {
        this.restHost = restHost;
    }

    public int getRestPort() {
        return restPort;
    }

    public void setRestPort(int restPort) {
        this.restPort = restPort;
    }

    public int getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(int rateLimit) {
        this.rateLimit = rateLimit;
    }

    public boolean isForwardEnabled() {
        return forwardEnabled;
    }

    public void setForwardEnabled(boolean forwardEnabled) {
        this.forwardEnabled = forwardEnabled;
    }

    public String getForwardPrefix() {
        return forwardPrefix;
    }

    public void setForwardPrefix(String forwardPrefix) {
        this.forwardPrefix = forwardPrefix;
    }

    public boolean isStripPrefix() {
        return stripPrefix;
    }

    public void setStripPrefix(boolean stripPrefix) {
        this.stripPrefix = stripPrefix;
    }

    public String getIncomingFormat() {
        return incomingFormat;
    }

    public void setIncomingFormat(String incomingFormat) {
        this.incomingFormat = incomingFormat;
    }

    public boolean isGroupChatEnabled() {
        return groupChatEnabled;
    }

    public void setGroupChatEnabled(boolean groupChatEnabled) {
        this.groupChatEnabled = groupChatEnabled;
    }

    public String getGroupChatPrefix() {
        return groupChatPrefix;
    }

    public void setGroupChatPrefix(String groupChatPrefix) {
        this.groupChatPrefix = groupChatPrefix;
    }

    public boolean isPrivateChatEnabled() {
        return privateChatEnabled;
    }

    public void setPrivateChatEnabled(boolean privateChatEnabled) {
        this.privateChatEnabled = privateChatEnabled;
    }

    public String getPrivateChatPrefix() {
        return privateChatPrefix;
    }

    public void setPrivateChatPrefix(String privateChatPrefix) {
        this.privateChatPrefix = privateChatPrefix;
    }

    public String getPrivateChatEchoFormat() {
        return privateChatEchoFormat;
    }

    public void setPrivateChatEchoFormat(String privateChatEchoFormat) {
        this.privateChatEchoFormat = privateChatEchoFormat;
    }

    public String getAiResponseFormat() {
        return aiResponseFormat;
    }

    public void setAiResponseFormat(String aiResponseFormat) {
        this.aiResponseFormat = aiResponseFormat;
    }

    public String getAiThinkingMessage() {
        return aiThinkingMessage;
    }

    public void setAiThinkingMessage(String aiThinkingMessage) {
        this.aiThinkingMessage = aiThinkingMessage;
    }

    public boolean isAiShowThinking() {
        return aiShowThinking;
    }

    public void setAiShowThinking(boolean aiShowThinking) {
        this.aiShowThinking = aiShowThinking;
    }

    public int getAiTimeoutSeconds() {
        return aiTimeoutSeconds;
    }

    public void setAiTimeoutSeconds(int aiTimeoutSeconds) {
        this.aiTimeoutSeconds = aiTimeoutSeconds;
    }

    public boolean isJoinNotifyEnabled() {
        return joinNotifyEnabled;
    }

    public void setJoinNotifyEnabled(boolean joinNotifyEnabled) {
        this.joinNotifyEnabled = joinNotifyEnabled;
    }

    public boolean isQuitNotifyEnabled() {
        return quitNotifyEnabled;
    }

    public void setQuitNotifyEnabled(boolean quitNotifyEnabled) {
        this.quitNotifyEnabled = quitNotifyEnabled;
    }

    public boolean isCommandEnabled() {
        return commandEnabled;
    }

    public void setCommandEnabled(boolean commandEnabled) {
        this.commandEnabled = commandEnabled;
    }

    public String getCommandFilterMode() {
        return commandFilterMode;
    }

    public void setCommandFilterMode(String commandFilterMode) {
        this.commandFilterMode = commandFilterMode;
    }

    public java.util.List<String> getCommandFilterList() {
        return commandFilterList;
    }

    public void setCommandFilterList(java.util.List<String> commandFilterList) {
        this.commandFilterList = commandFilterList;
    }

    public boolean isLogQueryEnabled() {
        return logQueryEnabled;
    }

    public void setLogQueryEnabled(boolean logQueryEnabled) {
        this.logQueryEnabled = logQueryEnabled;
    }

    public int getLogQueryMaxLines() {
        return logQueryMaxLines;
    }

    public void setLogQueryMaxLines(int logQueryMaxLines) {
        this.logQueryMaxLines = logQueryMaxLines;
    }

    public String getLogQueryFile() {
        return logQueryFile;
    }

    public void setLogQueryFile(String logQueryFile) {
        this.logQueryFile = logQueryFile;
    }

    public boolean isUpdateCheckEnabled() {
        return updateCheckEnabled;
    }

    public void setUpdateCheckEnabled(boolean updateCheckEnabled) {
        this.updateCheckEnabled = updateCheckEnabled;
    }

    public boolean isUpdateNotifyOps() {
        return updateNotifyOps;
    }

    public void setUpdateNotifyOps(boolean updateNotifyOps) {
        this.updateNotifyOps = updateNotifyOps;
    }

    public boolean isProxyModeEnabled() {
        return proxyModeEnabled;
    }

    public void setProxyModeEnabled(boolean proxyModeEnabled) {
        this.proxyModeEnabled = proxyModeEnabled;
    }

    public String getProxySecret() {
        return proxySecret;
    }

    public void setProxySecret(String proxySecret) {
        this.proxySecret = proxySecret;
    }

    public boolean isProxyBridgeEnabled() {
        return proxyBridgeEnabled;
    }

    public void setProxyBridgeEnabled(boolean proxyBridgeEnabled) {
        this.proxyBridgeEnabled = proxyBridgeEnabled;
    }

    public long getSyncedConfigVersion() {
        return syncedConfigVersion;
    }

    public void setSyncedConfigVersion(long syncedConfigVersion) {
        this.syncedConfigVersion = syncedConfigVersion;
    }

    public String getSyncedConfigHash() {
        return syncedConfigHash;
    }

    public void setSyncedConfigHash(String syncedConfigHash) {
        this.syncedConfigHash = syncedConfigHash;
    }

    public long getSyncedConfigUpdatedAt() {
        return syncedConfigUpdatedAt;
    }

    public void setSyncedConfigUpdatedAt(long syncedConfigUpdatedAt) {
        this.syncedConfigUpdatedAt = syncedConfigUpdatedAt;
    }
}
