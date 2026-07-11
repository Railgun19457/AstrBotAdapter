package io.github.railgun19457.astrbotadapter.platform.spi.support;

import io.github.railgun19457.astrbotadapter.platform.spi.PlatformCommandRegistry;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformConfigStore;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformEventSource;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformKeyStore;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformTextService;

import java.nio.file.Path;

public final class NoopPlatformComponents {
    public static final PlatformCommandRegistry COMMAND_REGISTRY = new PlatformCommandRegistry() {
        @Override
        public void registerCoreCommands(io.github.railgun19457.astrbotadapter.platform.spi.PlatformRuntimeControl runtimeControl) {
        }

        @Override
        public void unregisterAll() {
        }
    };

    public static final PlatformEventSource EVENT_SOURCE = new PlatformEventSource() {
        @Override
        public void registerChatListener(ChatListener listener) {
        }

        @Override
        public void registerJoinListener(PlayerJoinListener listener) {
        }

        @Override
        public void registerLeaveListener(PlayerLeaveListener listener) {
        }

        @Override
        public void unregisterAll() {
        }
    };

    public static final PlatformTextService TEXT_SERVICE = message -> message.plainText() != null ? message.plainText() : message.content();

    private NoopPlatformComponents() {
    }

    public static PlatformConfigStore configStore(Path dataDirectory) {
        return () -> dataDirectory;
    }

    public static PlatformKeyStore keyStore(Path keysDirectory) {
        return () -> keysDirectory;
    }
}
