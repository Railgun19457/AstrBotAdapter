package io.github.railgun19457.astrbotadapter.service.chat;

import com.google.gson.JsonObject;
import io.github.railgun19457.astrbotadapter.communication.MessageBroadcaster;
import io.github.railgun19457.astrbotadapter.communication.protocol.Message;
import io.github.railgun19457.astrbotadapter.communication.protocol.MessageType;
import io.github.railgun19457.astrbotadapter.core.config.PluginConfig;
import io.github.railgun19457.astrbotadapter.core.util.PlaceholderUtil;
import io.github.railgun19457.astrbotadapter.platform.PlatformAdapter;
import io.github.railgun19457.astrbotadapter.platform.common.CommonPlayer;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * AI聊天服务
 * 处理群聊和私聊AI对话
 */
public class ChatService {

    private final PluginConfig config;
    private final MessageBroadcaster broadcaster;
    private final PlatformAdapter platformAdapter;
    private final Logger logger;
    
    // 存储等待响应的请求
    private final Map<String, ChatRequest> pendingRequests = new ConcurrentHashMap<>();

    public ChatService(PluginConfig config, MessageBroadcaster broadcaster, 
                      PlatformAdapter platformAdapter, Logger logger) {
        this.config = config;
        this.broadcaster = broadcaster;
        this.platformAdapter = platformAdapter;
        this.logger = logger;
    }

    /**
     * 处理玩家聊天消息
     * @param playerUuid 玩家UUID
     * @param playerName 玩家名称
     * @param displayName 玩家显示名
     * @param message 消息内容
     * @return 是否处理了该消息（如果触发了AI聊天则返回true）
     */
    public boolean handlePlayerChat(UUID playerUuid, String playerName, String displayName, String message) {
        // 检查群聊前缀
        if (config.isGroupChatEnabled()) {
            String groupPrefix = config.getGroupChatPrefix();
            if (groupPrefix != null && !groupPrefix.isEmpty() && message.startsWith(groupPrefix)) {
                String content = message.substring(groupPrefix.length()).trim();
                if (!content.isEmpty()) {
                    sendChatRequest(playerUuid, playerName, displayName, content, ChatMode.GROUP);
                    return true;
                }
            }
        }

        // 检查私聊前缀
        if (config.isPrivateChatEnabled()) {
            String privatePrefix = config.getPrivateChatPrefix();
            if (privatePrefix != null && !privatePrefix.isEmpty() && message.startsWith(privatePrefix)) {
                String content = message.substring(privatePrefix.length()).trim();
                if (!content.isEmpty()) {
                    sendChatRequest(playerUuid, playerName, displayName, content, ChatMode.PRIVATE);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 发送聊天请求到Astrbot
     */
    public void sendChatRequest(UUID playerUuid, String playerName, String displayName, 
                                String content, ChatMode chatMode) {
        if (broadcaster == null || !broadcaster.isRunning()) {
            logger.warning("WebSocket未启用，无法发送AI聊天请求");
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
        payload.addProperty("chatMode", chatMode.getValue());
        payload.addProperty("content", content);
        
        JsonObject context = new JsonObject();
        context.addProperty("sessionId", chatMode == ChatMode.GROUP ? 
                "group-" + platformAdapter.getServerName() : 
                "private-" + playerUuid.toString());
        payload.add("context", context);

        // 构建消息
        Message message = Message.builder()
                .type(MessageType.CHAT_REQUEST)
                .source(source)
                .payload(payload)
                .build();

        // 存储等待响应的请求
        pendingRequests.put(message.getId(), new ChatRequest(playerUuid, chatMode));

        // 发送消息
        broadcaster.broadcast(message);
        
        logger.info("发送AI聊天请求: " + playerName + " -> " + content + " [" + chatMode + "]");
    }

    /**
     * 处理AI聊天响应
     */
    public void handleChatResponse(Message message) {
        if (message.getPayload() == null) {
            return;
        }

        JsonObject payload = message.getPayload();
        String requestId = message.getReplyTo();
        if (requestId == null && payload.has("requestId")) {
            requestId = payload.get("requestId").getAsString();
        }
        String content = payload.has("content") ? payload.get("content").getAsString() : null;

        if (content == null || content.isEmpty()) {
            return;
        }

        // 格式化响应消息
        String formattedMessage = PlaceholderUtil.replace(config.getAiResponseFormat(), "content", content);

        // 确定目标
        if (message.getTarget() != null) {
            String targetType = message.getTarget().getType();
            
            if ("BROADCAST".equals(targetType)) {
                // 群聊：广播给所有玩家
                platformAdapter.broadcastMessage(formattedMessage);
            } else if ("PLAYER".equals(targetType)) {
                // 私聊：发送给特定玩家
                String playerUuid = message.getTarget().getPlayerUuid();
                String playerName = message.getTarget().getPlayerName();
                
                Optional<CommonPlayer> player = Optional.empty();
                if (playerUuid != null) {
                    try {
                        player = platformAdapter.getPlayer(UUID.fromString(playerUuid));
                    } catch (Exception ignored) {}
                }
                if (player.isEmpty() && playerName != null) {
                    player = platformAdapter.getPlayer(playerName);
                }
                
                player.ifPresent(p -> platformAdapter.sendMessage(p, formattedMessage));
            }
        } else if (requestId != null) {
            // 通过requestId查找原始请求
            ChatRequest originalRequest = pendingRequests.remove(requestId);
            if (originalRequest != null) {
                if (originalRequest.chatMode == ChatMode.GROUP) {
                    platformAdapter.broadcastMessage(formattedMessage);
                } else {
                    platformAdapter.getPlayer(originalRequest.playerUuid)
                            .ifPresent(p -> platformAdapter.sendMessage(p, formattedMessage));
                }
            }
        }
    }

    /**
     * 检查消息是否应触发AI聊天
     */
    public boolean shouldTriggerChat(String message) {
        if (config.isGroupChatEnabled()) {
            String groupPrefix = config.getGroupChatPrefix();
            if (groupPrefix != null && !groupPrefix.isEmpty() && message.startsWith(groupPrefix)) {
                return true;
            }
        }

        if (config.isPrivateChatEnabled()) {
            String privatePrefix = config.getPrivateChatPrefix();
            if (privatePrefix != null && !privatePrefix.isEmpty() && message.startsWith(privatePrefix)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查消息是否触发AI私聊
     */
    public boolean isPrivateChatTrigger(String message) {
        if (config.isPrivateChatEnabled()) {
            String privatePrefix = config.getPrivateChatPrefix();
            if (privatePrefix != null && !privatePrefix.isEmpty() && message.startsWith(privatePrefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取配置
     */
    public PluginConfig getConfig() {
        return config;
    }

    /**
     * 聊天请求记录
     */
    private static class ChatRequest {
        final UUID playerUuid;
        final ChatMode chatMode;

        ChatRequest(UUID playerUuid, ChatMode chatMode) {
            this.playerUuid = playerUuid;
            this.chatMode = chatMode;
        }
    }
}
