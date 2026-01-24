package io.github.railgun19457.astrbotadapter.core.event.events;

import io.github.railgun19457.astrbotadapter.core.event.Cancellable;
import io.github.railgun19457.astrbotadapter.core.event.Event;
import io.github.railgun19457.astrbotadapter.service.chat.ChatMode;

import java.util.UUID;

/**
 * 聊天消息事件
 * 当玩家发送需要处理的聊天消息时触发
 */
public class ChatMessageEvent extends Event implements Cancellable {

    private final UUID playerUuid;
    private final String playerName;
    private final String displayName;
    private final String message;
    private final ChatMode chatMode;
    private final String serverName;
    
    private boolean cancelled = false;
    private String response = null;

    public ChatMessageEvent(UUID playerUuid, String playerName, String displayName, 
                           String message, ChatMode chatMode, String serverName) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.displayName = displayName;
        this.message = message;
        this.chatMode = chatMode;
        this.serverName = serverName;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getMessage() {
        return message;
    }

    public ChatMode getChatMode() {
        return chatMode;
    }

    public String getServerName() {
        return serverName;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
