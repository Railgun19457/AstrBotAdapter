package io.github.railgun19457.astrbotadapter.service.notification;

import com.google.gson.JsonObject;
import io.github.railgun19457.astrbotadapter.communication.MessageBroadcaster;
import io.github.railgun19457.astrbotadapter.communication.protocol.Message;
import io.github.railgun19457.astrbotadapter.communication.protocol.MessageType;
import io.github.railgun19457.astrbotadapter.core.config.PluginConfig;
import io.github.railgun19457.astrbotadapter.platform.PlatformAdapter;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * 通知服务
 * 处理玩家加入/离开等通知
 */
public class NotificationService {

    private final PluginConfig config;
    private final MessageBroadcaster broadcaster;
    private final PlatformAdapter platformAdapter;
    private final Logger logger;

    public NotificationService(PluginConfig config, MessageBroadcaster broadcaster, 
                              PlatformAdapter platformAdapter, Logger logger) {
        this.config = config;
        this.broadcaster = broadcaster;
        this.platformAdapter = platformAdapter;
        this.logger = logger;
    }

    /**
     * 发送玩家加入通知
     */
    public void notifyPlayerJoin(UUID playerUuid, String playerName, String displayName) {
        if (!config.isJoinNotifyEnabled()) {
            return;
        }
        if (broadcaster == null || !broadcaster.isRunning()) {
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
        payload.addProperty("action", "join");

        // 构建消息
        Message message = Message.builder()
                .type(MessageType.PLAYER_JOIN)
                .source(source)
                .payload(payload)
                .build();

        // 发送消息
        broadcaster.broadcast(message);
        
        logger.info("玩家加入通知已发送: " + playerName);
    }

    /**
     * 发送玩家离开通知
     */
    public void notifyPlayerQuit(UUID playerUuid, String playerName, String displayName, String reason) {
        if (!config.isQuitNotifyEnabled()) {
            return;
        }
        if (broadcaster == null || !broadcaster.isRunning()) {
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
        payload.addProperty("action", "quit");
        if (reason != null) {
            payload.addProperty("reason", reason);
        }

        // 构建消息
        Message message = Message.builder()
                .type(MessageType.PLAYER_QUIT)
                .source(source)
                .payload(payload)
                .build();

        // 发送消息
        broadcaster.broadcast(message);
        
        logger.info("玩家离开通知已发送: " + playerName);
    }

    /**
     * 发送状态更新
     */
    public void sendStatusUpdate() {
        if (broadcaster == null || !broadcaster.isRunning()) {
            return;
        }
        // 构建payload
        JsonObject payload = new JsonObject();
        payload.addProperty("onlinePlayers", platformAdapter.getOnlinePlayerCount());
        payload.addProperty("maxPlayers", platformAdapter.getMaxPlayers());
        payload.addProperty("uptime", platformAdapter.getServerUptime());

        // 构建消息
        Message message = Message.builder()
                .type(MessageType.STATUS_UPDATE)
                .source(Message.MessageSource.server(new Message.ServerInfo(
                        platformAdapter.getServerName(),
                        platformAdapter.getPlatformType().getDisplayName(),
                        platformAdapter.getServerVersion())))
                .payload(payload)
                .build();

        // 发送消息
        broadcaster.broadcast(message);
    }
}
