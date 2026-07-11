package io.github.railgun19457.astrbotadapter.platform.spi;

import java.util.UUID;

public record PlayerDetail(
    UUID uuid,
    String name,
    String displayName,
    boolean online,
    String serverId,
    String world,
    String gameMode,
    int pingMs,
    Double health,
    Double maxHealth,
    Integer foodLevel,
    Integer level,
    Double exp,
    Boolean operator,
    Boolean allowFlight,
    PlayerPosition location,
    Long firstPlayedAt,
    Long lastPlayedAt,
    Long onlineDurationMs
) {
    public PlayerDetail(UUID uuid, String name, String displayName, boolean online, String serverId, String world, String gameMode, int pingMs) {
        this(uuid, name, displayName, online, serverId, world, gameMode, pingMs, null, null, null, null, null, null, null, null, null, null, null);
    }
}
