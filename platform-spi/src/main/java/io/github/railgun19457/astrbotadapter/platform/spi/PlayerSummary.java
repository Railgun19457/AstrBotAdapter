package io.github.railgun19457.astrbotadapter.platform.spi;

import java.util.UUID;

public record PlayerSummary(UUID uuid, String name, String displayName, String serverId, String world, String gameMode, int pingMs) {
}
