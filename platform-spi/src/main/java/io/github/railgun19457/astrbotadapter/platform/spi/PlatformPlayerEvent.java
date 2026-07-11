package io.github.railgun19457.astrbotadapter.platform.spi;

import java.util.UUID;

public record PlatformPlayerEvent(UUID playerUuid, String playerName, int onlineCount, int maxPlayers, String reason) {
}
