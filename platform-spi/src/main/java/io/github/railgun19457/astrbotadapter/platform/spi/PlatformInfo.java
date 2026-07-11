package io.github.railgun19457.astrbotadapter.platform.spi;

import io.github.railgun19457.astrbotadapter.protocol.ServerRole;

public record PlatformInfo(
    String platform,
    String platformVersion,
    String minecraftVersion,
    String serverId,
    String displayName,
    ServerRole role
) {
}
