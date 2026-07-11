package io.github.railgun19457.astrbotadapter.platform.spi;

import java.util.UUID;

public record PlatformChatEvent(UUID playerUuid, String playerName, String message, String world) {
}
