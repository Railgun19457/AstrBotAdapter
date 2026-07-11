package io.github.railgun19457.astrbotadapter.platform.spi;

import io.github.railgun19457.astrbotadapter.protocol.ServerRole;
import io.github.railgun19457.astrbotadapter.protocol.Status;

public record ServerStatusItem(
    String serverId,
    String displayName,
    ServerRole role,
    Status status,
    String platform,
    String version,
    int onlineCount,
    int maxPlayers
) {
}
