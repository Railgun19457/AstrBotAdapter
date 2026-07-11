package io.github.railgun19457.astrbotadapter.platform.spi;

import io.github.railgun19457.astrbotadapter.protocol.ServerRole;

public record ServerSnapshot(String serverId, ServerRole role, int onlineCount, int maxPlayers, long reportedAt) {
}
