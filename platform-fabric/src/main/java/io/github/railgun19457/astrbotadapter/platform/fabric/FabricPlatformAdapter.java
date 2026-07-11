package io.github.railgun19457.astrbotadapter.platform.fabric;

import io.github.railgun19457.astrbotadapter.platform.spi.PlatformAdapter;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformChatService;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformCommandRegistry;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformCommandService;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformConfigStore;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformEventSource;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformInfo;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformKeyStore;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformLogService;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformLogger;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformPermissionService;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformPlayerService;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformScheduler;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformServerService;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformTextService;
import io.github.railgun19457.astrbotadapter.platform.spi.RuntimeMode;
import io.github.railgun19457.astrbotadapter.platform.spi.support.NoopPlatformComponents;
import io.github.railgun19457.astrbotadapter.protocol.ServerRole;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class FabricPlatformAdapter implements PlatformAdapter {
    private final MinecraftServer server;
    private final PlatformLogger logger;
    private final FabricPlatformScheduler scheduler;
    private final PlatformConfigStore configStore;
    private final PlatformKeyStore keyStore;
    private final PlatformCommandRegistry commandRegistry;
    private final PlatformEventSource eventSource;
    private final PlatformPlayerService playerService;
    private final PlatformChatService chatService;
    private final PlatformCommandService commandService;
    private final PlatformServerService serverService;
    private final PlatformTextService textService = message -> Text.literal(message.plainText());
    private final Path dataDirectory;
    private final RuntimeMode runtimeMode;

    public FabricPlatformAdapter(MinecraftServer server, Path dataDirectory, Logger logger) {
        this.server = server;
        this.dataDirectory = dataDirectory;
        this.runtimeMode = detectRuntimeMode(dataDirectory);
        this.logger = new PlatformLogger() {
            @Override
            public void info(String message) {
                logger.info(message);
            }

            @Override
            public void warn(String message) {
                logger.warn(message);
            }

            @Override
            public void error(String message, Throwable throwable) {
                logger.error(message, throwable);
            }
        };
        this.scheduler = new FabricPlatformScheduler(server);
        this.configStore = NoopPlatformComponents.configStore(dataDirectory);
        this.keyStore = NoopPlatformComponents.keyStore(dataDirectory.resolve("keys"));
        this.commandRegistry = new FabricCommandRegistry();
        this.eventSource = new FabricEventSource(server);
        this.playerService = new FabricPlayerService(server, scheduler);
        this.chatService = new FabricChatService(server, scheduler);
        this.commandService = new FabricCommandService(server, scheduler);
        this.serverService = new FabricServerService(server, scheduler, runtimeMode);
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    @Override
    public PlatformInfo platformInfo() {
        ServerRole role = runtimeMode == RuntimeMode.VELOCITY_BACKEND ? ServerRole.BACKEND : ServerRole.STANDALONE;
        return new PlatformInfo("fabric", "fabric", server.getVersion(), "standalone", "Fabric Server", role);
    }

    @Override
    public RuntimeMode runtimeMode() {
        return runtimeMode;
    }

    @Override
    public PlatformLogger logger() {
        return logger;
    }

    @Override
    public PlatformScheduler scheduler() {
        return scheduler;
    }

    @Override
    public PlatformConfigStore configStore() {
        return configStore;
    }

    @Override
    public PlatformKeyStore keyStore() {
        return keyStore;
    }

    @Override
    public PlatformCommandRegistry commandRegistry() {
        return commandRegistry;
    }

    @Override
    public PlatformEventSource eventSource() {
        return eventSource;
    }

    @Override
    public Optional<PlatformPlayerService> playerService() {
        return Optional.of(playerService);
    }

    @Override
    public Optional<PlatformChatService> chatService() {
        return Optional.of(chatService);
    }

    @Override
    public Optional<PlatformCommandService> commandService() {
        return Optional.of(commandService);
    }

    @Override
    public Optional<PlatformServerService> serverService() {
        return Optional.of(serverService);
    }

    @Override
    public Optional<PlatformLogService> logService() {
        return Optional.empty();
    }

    @Override
    public Optional<PlatformPermissionService> permissionService() {
        return Optional.empty();
    }

    @Override
    public PlatformTextService textService() {
        return textService;
    }

    private RuntimeMode detectRuntimeMode(Path dataDirectory) {
        RuntimeMode override = parseMode(System.getProperty("astrbotadapter.mode"));
        if (override != null) {
            return override;
        }
        override = parseMode(System.getenv("ASTRBOT_ADAPTER_MODE"));
        if (override != null) {
            return override;
        }
        Path configPath = dataDirectory.resolve("config.yml");
        if (!Files.exists(configPath)) {
            return RuntimeMode.STANDALONE;
        }
        try {
            List<String> lines = Files.readAllLines(configPath);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("mode:")) {
                    RuntimeMode mode = parseMode(trimmed.substring("mode:".length()).trim());
                    return mode == null ? RuntimeMode.STANDALONE : mode;
                }
            }
        } catch (IOException exception) {
            return RuntimeMode.STANDALONE;
        }
        return RuntimeMode.STANDALONE;
    }

    private RuntimeMode parseMode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase();
        try {
            return RuntimeMode.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
