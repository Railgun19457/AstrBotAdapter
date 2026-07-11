package io.github.railgun19457.astrbotadapter.core;

import io.github.railgun19457.astrbotadapter.platform.spi.RuntimeMode;

public record PluginConfig(RuntimeMode mode, String serverId, String displayName, FeatureConfig features) {
    public static PluginConfig defaults(RuntimeMode mode, String serverId, String displayName) {
        return new PluginConfig(mode, serverId, displayName, FeatureConfig.defaults());
    }
}
