package io.github.railgun19457.astrbotadapter;

import io.github.railgun19457.astrbotadapter.communication.auth.AuthManager;
import io.github.railgun19457.astrbotadapter.communication.rest.RestApiServer;
import io.github.railgun19457.astrbotadapter.communication.websocket.WebSocketServer;
import io.github.railgun19457.astrbotadapter.core.config.ConfigManager;
import io.github.railgun19457.astrbotadapter.core.config.PluginConfig;
import io.github.railgun19457.astrbotadapter.core.event.EventBus;
import io.github.railgun19457.astrbotadapter.core.i18n.I18NManager;
import io.github.railgun19457.astrbotadapter.core.i18n.MessageKey;
import io.github.railgun19457.astrbotadapter.platform.PlatformAdapter;
import io.github.railgun19457.astrbotadapter.service.chat.ChatService;
import io.github.railgun19457.astrbotadapter.service.forward.MessageForwardService;
import io.github.railgun19457.astrbotadapter.service.notification.NotificationService;

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

    // 通信组件
    protected WebSocketServer webSocketServer;
    protected RestApiServer restApiServer;

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
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        // 初始化核心组件
        initializeCore();

        // 初始化平台适配器
        initializePlatform();

        // 初始化通信组件
        initializeCommunication();

        // 初始化服务组件
        initializeServices();

        // 启动服务器
        startServers();

        logger.info(i18nManager.getMessage(MessageKey.PLUGIN_ENABLED));
    }

    /**
     * 初始化核心组件
     */
    private void initializeCore() {
        Path dataPath = dataFolder.toPath();
        
        // 配置管理器
        configManager = new ConfigManager(dataPath, logger);
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
     * 初始化通信组件
     */
    private void initializeCommunication() {
        PluginConfig config = configManager.getConfig();

        // WebSocket 服务器
        if (config.isWsEnabled()) {
            webSocketServer = new WebSocketServer(
                    config,
                    authManager,
                    eventBus,
                    platformAdapter,
                    logger
            );
            logger.info("WebSocket 服务器已配置");
        }

        // REST API 服务器
        if (config.isRestEnabled()) {
            restApiServer = new RestApiServer(
                    config,
                    authManager,
                    platformAdapter,
                    logger
            );
            logger.info("REST API 服务器已配置");
        }

        logger.info("通信组件初始化完成");
    }

    /**
     * 初始化服务组件
     */
    private void initializeServices() {
        PluginConfig config = configManager.getConfig();

        // 聊天服务
        chatService = new ChatService(config, webSocketServer, platformAdapter, logger);
        logger.info("聊天服务已初始化");

        // 消息转发服务
        messageForwardService = new MessageForwardService(config, webSocketServer, platformAdapter, logger);
        logger.info("消息转发服务已初始化");

        // 通知服务
        if (config.isJoinNotifyEnabled() || config.isQuitNotifyEnabled()) {
            notificationService = new NotificationService(config, webSocketServer, platformAdapter, logger);
            logger.info("通知服务已初始化");
        }

        logger.info("服务组件初始化完成");
    }

    /**
     * 启动服务器
     */
    private void startServers() {
        if (webSocketServer != null) {
            webSocketServer.start();
        }
        if (restApiServer != null) {
            restApiServer.start();
        }
    }

    /**
     * 关闭插件
     */
    protected void shutdown() {
        logger.info(i18nManager.getMessage(MessageKey.PLUGIN_DISABLING));

        // 停止服务器
        if (webSocketServer != null) {
            webSocketServer.stop();
        }
        if (restApiServer != null) {
            restApiServer.stop();
        }

        // 关闭平台适配器
        if (platformAdapter != null) {
            platformAdapter.shutdown();
        }

        // 保存配置
        configManager.saveConfig();

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

    public WebSocketServer getWebSocketServer() {
        return webSocketServer;
    }

    public RestApiServer getRestApiServer() {
        return restApiServer;
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
}
