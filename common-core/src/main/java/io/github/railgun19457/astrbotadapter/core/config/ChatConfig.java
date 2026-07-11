package io.github.railgun19457.astrbotadapter.core.config;

public record ChatConfig(boolean forwardEnabled, String forwardPrefix, String minecraftToExternalFormat, String externalToMinecraftFormat, boolean joinForwardEnabled, boolean leaveForwardEnabled) {
    public ChatConfig(boolean forwardEnabled, String forwardPrefix, String minecraftToExternalFormat, String externalToMinecraftFormat) {
        this(forwardEnabled, forwardPrefix, minecraftToExternalFormat, externalToMinecraftFormat, true, true);
    }
}
