package io.github.railgun19457.astrbotadapter.service.forward;

import com.google.gson.JsonObject;
import io.github.railgun19457.astrbotadapter.communication.protocol.Message;
import io.github.railgun19457.astrbotadapter.communication.protocol.MessageType;
import io.github.railgun19457.astrbotadapter.communication.websocket.WebSocketServer;
import io.github.railgun19457.astrbotadapter.core.config.PluginConfig;
import io.github.railgun19457.astrbotadapter.core.util.PlaceholderUtil;
import io.github.railgun19457.astrbotadapter.platform.PlatformAdapter;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * 消息转发服务
 * 处理MC和外部平台之间的消息互传
 */
public class MessageForwardService {

    private final PluginConfig config;
    private final WebSocketServer wsServer;
    private final PlatformAdapter platformAdapter;
    private final Logger logger;

    public MessageForwardService(PluginConfig config, WebSocketServer wsServer, 
                                PlatformAdapter platformAdapter, Logger logger) {
        this.config = config;
        this.wsServer = wsServer;
        this.platformAdapter = platformAdapter;
        this.logger = logger;
    }

    /**
     * 处理玩家消息并转发到外部
     * @return 是否进行了转发
     */
    public boolean handlePlayerMessage(UUID playerUuid, String playerName, String displayName, String message) {
        if (!config.isForwardEnabled()) {
            return false;
        }

        String forwardPrefix = config.getForwardPrefix();
        
        // 检查前缀
        if (forwardPrefix != null && !forwardPrefix.isEmpty()) {
            if (!message.startsWith(forwardPrefix)) {
                return false;
            }
            // 移除前缀
            if (config.isStripPrefix()) {
                message = message.substring(forwardPrefix.length()).trim();
            }
        }

        if (message.isEmpty()) {
            return false;
        }

        // 发送转发消息
        forwardToExternal(playerUuid, playerName, displayName, message);
        return true;
    }

    /**
     * 转发消息到外部
     */
    public void forwardToExternal(UUID playerUuid, String playerName, String displayName, String content) {
        if (wsServer == null || !wsServer.isRunning()) {
            logger.warning("WebSocket未启用，无法转发消息");
            return;
        }
        // 构建来源信息
        Message.PlayerInfo playerInfo = new Message.PlayerInfo(
                playerUuid.toString(), playerName, displayName);
        Message.ServerInfo serverInfo = new Message.ServerInfo(
                platformAdapter.getServerName(),
                platformAdapter.getPlatformType().getDisplayName(),
                platformAdapter.getServerVersion());
        
        Message.MessageSource source = Message.MessageSource.player(playerInfo, serverInfo);

        // 构建payload
        JsonObject payload = new JsonObject();
        payload.addProperty("content", content);

        // 构建消息
        Message message = Message.builder()
                .type(MessageType.MESSAGE_FORWARD)
                .source(source)
                .payload(payload)
                .build();

        // 发送消息
        wsServer.broadcast(message);
        
        logger.info("消息已转发: " + playerName + " -> " + content);
    }

    /**
     * 处理外部消息并在MC中显示
     */
    public void handleIncomingMessage(Message message) {
        if (message.getPayload() == null) {
            return;
        }

        JsonObject payload = message.getPayload();
        String platform = "外部";
        String username = "未知";
        String content = "";

        if (payload.has("source") && payload.get("source").isJsonObject()) {
            JsonObject source = payload.getAsJsonObject("source");
            if (source.has("platform")) {
                platform = source.get("platform").getAsString();
            }
            if (source.has("userName")) {
                username = source.get("userName").getAsString();
            }
        }

        if (payload.has("content")) {
            content = payload.get("content").getAsString();
        }

        // 兼容旧字段
        if (payload.has("platform")) {
            platform = payload.get("platform").getAsString();
        }
        if (payload.has("username")) {
            username = payload.get("username").getAsString();
        }

        if (content.isEmpty()) {
            return;
        }

        // 格式化消息
        String formattedMessage = PlaceholderUtil.replace(
                config.getIncomingFormat(),
                "platform", platform,
                "username", username,
                "content", content
        );

        // 广播消息
        platformAdapter.broadcastMessage(formattedMessage);
        
        logger.info("收到外部消息: [" + platform + "] " + username + " -> " + content);
    }

    /**
     * 检查消息是否应该被转发
     */
    public boolean shouldForward(String message) {
        if (!config.isForwardEnabled()) {
            return false;
        }

        String forwardPrefix = config.getForwardPrefix();
        
        // 如果没有设置前缀，则转发所有消息
        if (forwardPrefix == null || forwardPrefix.isEmpty()) {
            return true;
        }

        return message.startsWith(forwardPrefix);
    }
}
