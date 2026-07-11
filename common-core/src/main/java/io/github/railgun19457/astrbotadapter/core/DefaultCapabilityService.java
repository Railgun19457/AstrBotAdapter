package io.github.railgun19457.astrbotadapter.core;

import io.github.railgun19457.astrbotadapter.platform.spi.PlatformAdapter;
import io.github.railgun19457.astrbotadapter.platform.spi.RuntimeMode;

import java.util.LinkedHashSet;
import java.util.Set;

public final class DefaultCapabilityService implements CapabilityService {
    @Override
    public Set<String> capabilities(PlatformAdapter platform, FeatureConfig config) {
        LinkedHashSet<String> capabilities = new LinkedHashSet<>();
        capabilities.add(CapabilityNames.CORE_V1);
        capabilities.add(CapabilityNames.AUTH_ED25519_V1);

        if (config.chat() && platform.chatService().isPresent()) {
            capabilities.add(CapabilityNames.CHAT_V1);
        }
        if (config.player() && platform.playerService().isPresent()) {
            capabilities.add(CapabilityNames.PLAYER_V1);
        }
        if (config.server() && platform.serverService().isPresent()) {
            capabilities.add(CapabilityNames.SERVER_V1);
        }
        if (config.command() && platform.commandService().isPresent()) {
            capabilities.add(CapabilityNames.COMMAND_V1);
        }
        if (config.ai()) {
            capabilities.add(CapabilityNames.AI_V1);
        }
        if (config.log() && platform.logService().isPresent()) {
            capabilities.add(CapabilityNames.LOG_V1);
        }
        if (config.cluster() && platform.runtimeMode() != RuntimeMode.STANDALONE) {
            capabilities.add(CapabilityNames.CLUSTER_V1);
        }
        if (config.config() && platform.runtimeMode() != RuntimeMode.STANDALONE) {
            capabilities.add(CapabilityNames.CONFIG_V1);
        }

        return Set.copyOf(capabilities);
    }
}
