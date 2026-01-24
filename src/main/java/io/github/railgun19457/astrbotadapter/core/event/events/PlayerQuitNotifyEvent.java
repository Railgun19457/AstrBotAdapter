package io.github.railgun19457.astrbotadapter.core.event.events;

import io.github.railgun19457.astrbotadapter.core.event.Event;

import java.util.UUID;

/**
 * 玩家离开通知事件
 */
public class PlayerQuitNotifyEvent extends Event {

    private final UUID playerUuid;
    private final String playerName;
    private final String displayName;
    private final String serverName;
    private final String quitReason;

    public PlayerQuitNotifyEvent(UUID playerUuid, String playerName, String displayName, 
                                 String serverName, String quitReason) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.displayName = displayName;
        this.serverName = serverName;
        this.quitReason = quitReason;
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

    public String getQuitReason() {
        return quitReason;
    }
}
