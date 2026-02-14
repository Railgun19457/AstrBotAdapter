package io.github.railgun19457.astrbotadapter;

import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import io.github.railgun19457.astrbotadapter.communication.protocol.Message;
import io.github.railgun19457.astrbotadapter.communication.protocol.MessageType;
import io.github.railgun19457.astrbotadapter.communication.proxy.VelocityProxyBridge;
import io.github.railgun19457.astrbotadapter.core.config.ConfigManager.ConfigPlatform;
import io.github.railgun19457.astrbotadapter.platform.velocity.VelocityAdapter;
import io.github.railgun19457.astrbotadapter.platform.velocity.listener.VelocityPlayerListener;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;

/**
 * AstrbotAdapter Velocity插件入口
 */
@Plugin(
        id = "astrbotadapter",
        name = "AstrbotAdapter",
        version = "2.0.0",
        description = "Astrbot框架的Minecraft服务器适配器",
        authors = {"Railgun19457"}
)
public class AstrbotAdapterVelocity extends AstrbotAdapterPlugin {

    private final ProxyServer proxy;
    private final Logger velocityLogger;
    private final Path dataDirectory;
    private VelocityProxyBridge proxyBridge;

    @Inject
    public AstrbotAdapterVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.velocityLogger = logger;
        this.dataDirectory = dataDirectory;
        
        // 创建Java Logger包装
        this.logger = java.util.logging.Logger.getLogger("AstrbotAdapter");
        this.logger.setUseParentHandlers(false);
        this.logger.addHandler(new VelocityLogHandler(velocityLogger));
        
        this.dataFolder = dataDirectory.toFile();
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        initialize();
        registerVelocityListeners();

        // Velocity always acts as proxy bridge
        initializeProxyBridge();
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (proxyBridge != null) {
            proxyBridge.shutdown();
        }
        shutdown();
    }

    @Override
    protected void initializePlatform() {
        platformAdapter = new VelocityAdapter(this, proxy, velocityLogger);
        platformAdapter.initialize();
        logger.info("Velocity平台适配器已初始化");
    }

    @Override
    protected ConfigPlatform getConfigPlatform() {
        return ConfigPlatform.VELOCITY;
    }

    /**
     * Initialize the proxy bridge for aggregating backend server data.
     */
    private void initializeProxyBridge() {
        proxyBridge = new VelocityProxyBridge(this, proxy, configManager.getConfig(), dataDirectory, logger);
        proxyBridge.initialize();

        // Wire up the broadcaster so bridge can forward to Astrbot
        if (unifiedServer != null) {
            proxyBridge.setBroadcaster(unifiedServer);
        }

        // Set up event handler for backend events
        proxyBridge.setEventHandler(this::handleProxyBridgeEvent);

        // Register the bridge as a Velocity event listener
        proxy.getEventManager().register(this, proxyBridge);

        logger.info("代理桥接已初始化，等待后端服务器连接...");
    }

    /**
     * Handle events from the proxy bridge (backend server reports).
     * Backend servers handle AI chat locally (prefix detection, private chat cancellation, echo).
     * The proxy only forwards the resulting chat/forward requests to Astrbot.
     */
    private void handleProxyBridgeEvent(VelocityProxyBridge.ProxyBridgeEvent event) {
        JsonObject data = event.getData();
        if (data == null) return;

        switch (event.getType()) {
            case CHAT_MESSAGE -> {
                // Backend has already handled AI chat triggers locally.
                // We receive the raw chat message and forward it for message forwarding only.
                String playerUuid = data.has("playerUuid") ? data.get("playerUuid").getAsString() : null;
                String playerName = data.has("playerName") ? data.get("playerName").getAsString() : "Unknown";
                String displayName = data.has("displayName") ? data.get("displayName").getAsString() : playerName;
                String message = data.has("message") ? data.get("message").getAsString() : "";

                if (!message.isEmpty() && messageForwardService != null
                        && messageForwardService.shouldForward(message)) {
                    messageForwardService.handlePlayerMessage(
                            playerUuid != null ? UUID.fromString(playerUuid) : UUID.randomUUID(),
                            playerName, displayName, message);
                }
            }
            case AI_CHAT_REQUEST -> {
                // Backend detected an AI chat trigger and sent us the processed request.
                // Forward it to Astrbot via ChatService.
                String playerUuid = data.has("playerUuid") ? data.get("playerUuid").getAsString() : null;
                String playerName = data.has("playerName") ? data.get("playerName").getAsString() : "Unknown";
                String displayName = data.has("displayName") ? data.get("displayName").getAsString() : playerName;
                String content = data.has("content") ? data.get("content").getAsString() : "";
                String chatMode = data.has("chatMode") ? data.get("chatMode").getAsString() : "GROUP";

                if (!content.isEmpty() && chatService != null) {
                    io.github.railgun19457.astrbotadapter.service.chat.ChatMode mode =
                            io.github.railgun19457.astrbotadapter.service.chat.ChatMode.fromString(chatMode);
                    chatService.sendChatRequest(
                            playerUuid != null ? UUID.fromString(playerUuid) : UUID.randomUUID(),
                            playerName, displayName, content, mode);
                }
            }
            case PLAYER_JOIN -> {
                if (notificationService != null) {
                    String playerUuid = data.has("playerUuid") ? data.get("playerUuid").getAsString() : null;
                    String playerName = data.has("playerName") ? data.get("playerName").getAsString() : "Unknown";
                    String displayName = data.has("displayName") ? data.get("displayName").getAsString() : playerName;

                    if (playerUuid != null) {
                        notificationService.notifyPlayerJoin(
                                UUID.fromString(playerUuid), playerName, displayName);
                    }
                }
            }
            case PLAYER_QUIT -> {
                if (notificationService != null) {
                    String playerUuid = data.has("playerUuid") ? data.get("playerUuid").getAsString() : null;
                    String playerName = data.has("playerName") ? data.get("playerName").getAsString() : "Unknown";
                    String displayName = data.has("displayName") ? data.get("displayName").getAsString() : playerName;
                    String reason = data.has("reason") ? data.get("reason").getAsString() : "QUIT";

                    if (playerUuid != null) {
                        notificationService.notifyPlayerQuit(
                                UUID.fromString(playerUuid), playerName, displayName, reason);
                    }
                }
            }
            case LOG_REPORT -> {
                // Log reports can be cached or forwarded as needed
                logger.fine("Received log report from backend: " + event.getServerName());
            }
        }
    }

    /**
     * 注册Velocity事件监听器
     */
    private void registerVelocityListeners() {
        // Chat forwarding is handled via PMC from backend servers (handleProxyBridgeEvent),
        // so no VelocityChatListener is needed here.

        // 注册玩家监听器
        proxy.getEventManager().register(this, 
                new VelocityPlayerListener(this, proxy, notificationService));
        
        logger.info("Velocity事件监听器已注册");
    }

    /**
     * Get the proxy bridge (only available when proxy bridge is enabled).
     */
    public VelocityProxyBridge getProxyBridge() {
        return proxyBridge;
    }

    /**
     * Override command routing to support sending commands to backend servers.
     */
    @Override
    protected void routeCommandToBackend(io.github.railgun19457.astrbotadapter.communication.protocol.Message message,
                                          String targetServer, String command,
                                          String executor, String playerUuid) {
        if (proxyBridge == null) {
            super.routeCommandToBackend(message, targetServer, command, executor, playerUuid);
            return;
        }

        boolean sent = proxyBridge.sendCommandToBackend(
                targetServer, command, executor, playerUuid, message.getId());

        if (!sent) {
            JsonObject payload = new JsonObject();
            payload.addProperty("code", io.github.railgun19457.astrbotadapter.communication.protocol.ErrorCode.COMMAND_EXECUTE_FAILED.getCode());
            payload.addProperty("message", "无法将指令发送到后端服务器: " + targetServer);
            payload.addProperty("detail", "后端服务器未连接或无在线玩家");

            io.github.railgun19457.astrbotadapter.communication.protocol.Message error =
                    io.github.railgun19457.astrbotadapter.communication.protocol.Message.builder()
                    .type(MessageType.ERROR)
                    .replyTo(message.getId())
                    .payload(payload)
                    .build();

            if (unifiedServer != null) {
                unifiedServer.broadcast(error);
            }
        }
    }

    /**
     * Velocity Logger适配器
     */
    private static class VelocityLogHandler extends java.util.logging.Handler {
        private final Logger velocityLogger;

        public VelocityLogHandler(Logger velocityLogger) {
            this.velocityLogger = velocityLogger;
        }

        @Override
        public void publish(java.util.logging.LogRecord record) {
            String message = record.getMessage();
            java.util.logging.Level level = record.getLevel();
            
            if (level == java.util.logging.Level.SEVERE) {
                velocityLogger.error(message);
            } else if (level == java.util.logging.Level.WARNING) {
                velocityLogger.warn(message);
            } else if (level == java.util.logging.Level.INFO) {
                velocityLogger.info(message);
            } else {
                velocityLogger.debug(message);
            }
        }

        @Override
        public void flush() {}

        @Override
        public void close() throws SecurityException {}
    }
}
