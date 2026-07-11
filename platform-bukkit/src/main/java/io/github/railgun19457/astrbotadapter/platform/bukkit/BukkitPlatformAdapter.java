package io.github.railgun19457.astrbotadapter.platform.bukkit;

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
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

final class BukkitPlatformAdapter implements PlatformAdapter {
    private final JavaPlugin plugin;
    private final PlatformLogger logger;
    private final PlatformScheduler scheduler;
    private final PlatformConfigStore configStore;
    private final PlatformKeyStore keyStore;
    private final PlatformCommandRegistry commandRegistry;
    private final PlatformEventSource eventSource;
    private final PlatformPlayerService playerService;
    private final PlatformChatService chatService;
    private final PlatformCommandService commandService;
    private final PlatformServerService serverService;
    private final PlatformLogService logService;
    private final PlatformPermissionService permissionService;

    BukkitPlatformAdapter(JavaPlugin plugin) {
        this.plugin = plugin;
        Path dataDirectory = plugin.getDataFolder().toPath();
        this.logger = new BukkitPlatformLogger(plugin);
        this.scheduler = new BukkitPlatformScheduler(plugin);
        this.configStore = NoopPlatformComponents.configStore(dataDirectory);
        this.keyStore = NoopPlatformComponents.keyStore(dataDirectory.resolve("keys"));
        this.commandRegistry = new BukkitCommandRegistry(plugin);
        this.eventSource = new BukkitEventSource(plugin);
        this.playerService = new BukkitPlayerService(plugin, scheduler);
        this.chatService = new BukkitChatService(plugin, scheduler);
        this.commandService = new BukkitCommandService(plugin, scheduler);
        this.serverService = new BukkitServerService(plugin, scheduler);
        this.logService = new BukkitLogService(plugin, scheduler);
        this.permissionService = new BukkitPermissionService(plugin);
    }

    @Override
    public PlatformInfo platformInfo() {
        return new PlatformInfo(
            plugin.getServer().getName().toLowerCase(Locale.ROOT),
            plugin.getServer().getBukkitVersion(),
            plugin.getServer().getMinecraftVersion(),
            "standalone",
            plugin.getServer().getName(),
            ServerRole.STANDALONE
        );
    }

    @Override
    public RuntimeMode runtimeMode() {
        return RuntimeMode.STANDALONE;
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
        return Optional.of(logService);
    }

    @Override
    public Optional<PlatformPermissionService> permissionService() {
        return Optional.of(permissionService);
    }

    @Override
    public PlatformTextService textService() {
        return NoopPlatformComponents.TEXT_SERVICE;
    }
}
