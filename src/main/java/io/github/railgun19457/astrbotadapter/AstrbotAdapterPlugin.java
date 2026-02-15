package io.github.railgun19457.astrbotadapter;

import io.github.railgun19457.astrbotadapter.communication.UnifiedServer;
import io.github.railgun19457.astrbotadapter.communication.auth.AuthManager;
import io.github.railgun19457.astrbotadapter.communication.protocol.ErrorCode;
import io.github.railgun19457.astrbotadapter.communication.protocol.Message;
import io.github.railgun19457.astrbotadapter.communication.protocol.MessageType;
import io.github.railgun19457.astrbotadapter.core.config.ConfigManager;
import io.github.railgun19457.astrbotadapter.core.config.ConfigManager.ConfigPlatform;
import io.github.railgun19457.astrbotadapter.core.config.PluginConfig;
import io.github.railgun19457.astrbotadapter.core.event.EventBus;
import io.github.railgun19457.astrbotadapter.core.i18n.I18NManager;
import io.github.railgun19457.astrbotadapter.core.i18n.MessageKey;
import io.github.railgun19457.astrbotadapter.core.util.JsonUtil;
import io.github.railgun19457.astrbotadapter.platform.PlatformAdapter;
import io.github.railgun19457.astrbotadapter.service.command.CommandExecutionService;
import io.github.railgun19457.astrbotadapter.service.chat.ChatService;
import io.github.railgun19457.astrbotadapter.service.forward.MessageForwardService;
import io.github.railgun19457.astrbotadapter.service.notification.NotificationService;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.io.File;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * AstrbotAdapter 抽象插件基类
 * 提供跨平台通用的初始化逻辑
 */
public abstract class AstrbotAdapterPlugin {

    private static AstrbotAdapterPlugin instance;

    protected Logger logger;
    protected File dataFolder;

    // 核心组件
    protected ConfigManager configManager;
    protected I18NManager i18nManager;
    protected EventBus eventBus;
    protected AuthManager authManager;

    // 通信组件（统一服务器）
    protected UnifiedServer unifiedServer;

    // 服务组件
    protected ChatService chatService;
    protected MessageForwardService messageForwardService;
    protected NotificationService notificationService;

    // 平台适配器
    protected PlatformAdapter platformAdapter;

    /**
     * 获取插件实例
     */
    public static AstrbotAdapterPlugin getInstance() {
        return instance;
    }

    /**
     * 初始化插件
     */
    protected void initialize() {
        instance = this;

        // 确保数据目录存在
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            logger.warning("无法创建数据目录: " + dataFolder.getAbsolutePath());
        }

        // 初始化核心组件
        initializeCore();

        // 初始化平台适配器
        initializePlatform();

        // 初始化通信组件
        initializeCommunication();

        // 初始化服务组件
        initializeServices();

        // Platform-specific pre-start hook (e.g. Velocity initializes proxy bridge)
        initializeBeforeStart();

        // 启动服务器
        startServers();

        logger.info(i18nManager.getMessage(MessageKey.PLUGIN_ENABLED));
    }

    /**
     * Hook for platform-specific initialization before the server starts.
     * Override in Velocity to initialize the proxy bridge before routes are registered.
     */
    protected void initializeBeforeStart() {
        // Default: no-op
    }

    /**
     * 初始化核心组件
     */
    private void initializeCore() {
        Path dataPath = dataFolder.toPath();
        
        // 配置管理器 (use platform-specific config)
        configManager = new ConfigManager(dataPath, logger, getConfigPlatform());
        configManager.loadConfig();

        // 国际化管理器
        i18nManager = new I18NManager(dataPath, logger);
        i18nManager.loadLanguage(configManager.getConfig().getLanguage());

        // 事件总线
        eventBus = new EventBus(logger, configManager.getConfig().isDebug());

        // 认证管理器
        authManager = new AuthManager(configManager, logger);
        authManager.initialize();

        logger.info("核心组件初始化完成");
    }

    /**
     * 初始化平台适配器（子类实现）
     */
    protected abstract void initializePlatform();

    /**
     * Get the config platform type. Override in Velocity to return VELOCITY.
     */
    protected ConfigPlatform getConfigPlatform() {
        return ConfigPlatform.BACKEND;
    }

    /**
     * 初始化通信组件
     */
    private void initializeCommunication() {
        PluginConfig config = configManager.getConfig();

        // Proxy mode: backend does not start WS/REST server
        if (config.isProxyModeEnabled()) {
            logger.info("代理模式已启用，跳过WS/REST服务器初始化");
            logger.info("通信组件初始化完成 (代理模式)");
            return;
        }

        // 使用统一服务器（WebSocket + REST API 共用端口）
        if (config.isWsEnabled() || config.isRestEnabled()) {
            unifiedServer = new UnifiedServer(
                    config,
                    authManager,
                    eventBus,
                    platformAdapter,
                    logger
            );
            logger.info("统一服务器已配置 (端口: " + config.getServerPort() + ")");
        }

        logger.info("通信组件初始化完成");
    }

    /**
     * 初始化服务组件
     */
    private void initializeServices() {
        PluginConfig config = configManager.getConfig();

        // 聊天服务（使用统一服务器）
        chatService = new ChatService(config, unifiedServer, platformAdapter, logger);
        logger.info("聊天服务已初始化");

        // 消息转发服务
        messageForwardService = new MessageForwardService(config, unifiedServer, platformAdapter, logger);
        logger.info("消息转发服务已初始化");

        // 通知服务
        if (config.isJoinNotifyEnabled() || config.isQuitNotifyEnabled()) {
            notificationService = new NotificationService(config, unifiedServer, platformAdapter, logger);
            logger.info("通知服务已初始化");
        }

        // WebSocket入站消息处理
        if (unifiedServer != null) {
            unifiedServer.setMessageHandler(this::handleWebSocketMessage);
        }

        logger.info("服务组件初始化完成");
    }

    /**
     * 启动服务器
     */
    private void startServers() {
        if (configManager.getConfig().isProxyModeEnabled()) {
            logger.info("代理模式下不启动WS/REST服务器");
            return;
        }
        if (unifiedServer != null) {
            unifiedServer.start();
        }
    }

    /**
     * 关闭插件
     */
    protected void shutdown() {
        logger.info(i18nManager.getMessage(MessageKey.PLUGIN_DISABLING));

        // 停止服务器
        if (unifiedServer != null) {
            unifiedServer.stop();
        }

        // 关闭平台适配器
        if (platformAdapter != null) {
            platformAdapter.shutdown();
        }

        // 保存配置
        if (configManager != null) {
            configManager.saveConfig();
        }

        logger.info(i18nManager.getMessage(MessageKey.PLUGIN_DISABLED));
        instance = null;
    }

    /**
     * 重载配置
     */
    public void reload() {
        logger.info(i18nManager.getMessage(MessageKey.COMMAND_RELOAD_RELOADING));

        // 重载配置
        configManager.loadConfig();

        // 重载语言
        i18nManager.loadLanguage(configManager.getConfig().getLanguage());

        // 重载认证
        authManager.reload();

        logger.info(i18nManager.getMessage(MessageKey.COMMAND_RELOAD_SUCCESS));
    }

    // Getters

    public Logger getLogger() {
        return logger;
    }

    public File getDataFolder() {
        return dataFolder;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public I18NManager getI18NManager() {
        return i18nManager;
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public AuthManager getAuthManager() {
        return authManager;
    }

    public UnifiedServer getUnifiedServer() {
        return unifiedServer;
    }

    public ChatService getChatService() {
        return chatService;
    }

    public MessageForwardService getMessageForwardService() {
        return messageForwardService;
    }

    public NotificationService getNotificationService() {
        return notificationService;
    }

    public PlatformAdapter getPlatformAdapter() {
        return platformAdapter;
    }

    private void handleWebSocketMessage(Message message) {
        if (message == null || message.getType() == null) {
            return;
        }

        switch (message.getType()) {
            case CHAT_RESPONSE -> {
                if (chatService != null) {
                    chatService.handleChatResponse(message);
                }
            }
            case MESSAGE_INCOMING -> {
                if (messageForwardService != null) {
                    messageForwardService.handleIncomingMessage(message);
                }
            }
            case COMMAND_REQUEST -> handleCommandRequest(message);
            default -> {
                // ignore other types
            }
        }
    }

    private void handleCommandRequest(Message message) {
        if (unifiedServer == null) {
            return;
        }

        PluginConfig config = configManager.getConfig();
        if (!config.isCommandEnabled()) {
            sendCommandError(message, ErrorCode.FEATURE_DISABLED, "外部指令执行功能已禁用");
            return;
        }

        JsonObject payload = message.getPayload();
        if (payload == null) {
            sendCommandError(message, ErrorCode.REQUEST_PARAM_MISSING, "缺少请求体");
            return;
        }

        String command = JsonUtil.getString(payload, "command", null);
        CommandExecutionService.ValidationResult validation =
                CommandExecutionService.validateAndNormalizeCommand(command, config);
        if (!validation.isValid()) {
            sendCommandError(message, validation.errorCode(), validation.detail());
            return;
        }
        command = validation.command();

        String executor = JsonUtil.getString(payload, "executor", CommandExecutionService.EXECUTOR_CONSOLE);
        String playerUuid = JsonUtil.getString(payload, "playerUuid", null);

        // Check if this command should be routed to a backend server via proxy bridge
        String targetServer = JsonUtil.getString(payload, "targetServer", null);
        if (targetServer != null) {
            targetServer = targetServer.trim();
        }
        if (targetServer != null && !targetServer.isEmpty()) {
            routeCommandToBackend(message, targetServer, command, executor, playerUuid);
            return;
        }

        // Execute locally on this server (proxy or standalone)
        CommandExecutionService.ExecutionResult result = CommandExecutionService.executeLocalCommand(
                platformAdapter,
                command,
                executor,
                playerUuid,
                logger
        );
        if (!result.success()) {
            sendCommandError(message,
                    result.errorCode() == null ? ErrorCode.COMMAND_EXECUTE_FAILED : result.errorCode(),
                    result.errorMessage());
            return;
        }

        JsonObject responsePayload = new JsonObject();
        responsePayload.addProperty("success", true);
        responsePayload.addProperty("command", result.command());
        responsePayload.addProperty("executionTime", result.executionTime());
        responsePayload.addProperty("output", "Command executed");

        if (!result.logs().isEmpty()) {
            JsonArray logArray = new JsonArray();
            for (String log : result.logs()) {
                logArray.add(log);
            }
            responsePayload.add("logs", logArray);
            responsePayload.addProperty("output", String.join("\n", result.logs()));
        }
        responsePayload.add("errorCode", JsonNull.INSTANCE);
        responsePayload.add("errorMessage", JsonNull.INSTANCE);

        Message response = Message.builder()
                .type(MessageType.COMMAND_RESPONSE)
                .replyTo(message.getId())
                .payload(responsePayload)
                .build();

        unifiedServer.broadcast(response);
    }

    private void sendCommandError(Message request, ErrorCode errorCode, String detail) {
        if (unifiedServer == null) {
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("code", errorCode.getCode());
        payload.addProperty("message", errorCode.getMessage());
        if (detail != null) {
            payload.addProperty("detail", detail);
        }

        Message error = Message.builder()
                .type(MessageType.ERROR)
                .replyTo(request.getId())
                .payload(payload)
                .build();

        unifiedServer.broadcast(error);
    }

    /**
     * Route a command to a backend server via the proxy bridge.
     * Only available on Velocity with proxy bridge enabled.
     */
    protected void routeCommandToBackend(Message message, String targetServer,
                                          String command, String executor, String playerUuid) {
        // Default implementation: not supported (overridden in Velocity)
        sendCommandError(message, ErrorCode.FEATURE_DISABLED,
                "Command routing to backend servers is only available on Velocity with proxy bridge enabled");
    }
}
