package io.github.railgun19457.astrbotadapter.core.event.events;

import io.github.railgun19457.astrbotadapter.core.event.Event;

import java.util.UUID;

/**
 * 玩家加入通知事件
 */
public class PlayerJoinNotifyEvent extends Event {

    private final UUID playerUuid;
    private final String playerName;
    private final String displayName;
    private final String serverName;

    public PlayerJoinNotifyEvent(UUID playerUuid, String playerName, String displayName, String serverName) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.displayName = displayName;
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

    public String getServerName() {
        return serverName;
    }
}
