package io.github.railgun19457.astrbotadapter.platform.bukkit;

import io.github.railgun19457.astrbotadapter.platform.spi.PlatformScheduler;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformServerService;
import io.github.railgun19457.astrbotadapter.platform.spi.ServerSnapshot;
import io.github.railgun19457.astrbotadapter.platform.spi.ServerStatusItem;
import io.github.railgun19457.astrbotadapter.protocol.ServerRole;
import io.github.railgun19457.astrbotadapter.protocol.Status;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public final class BukkitServerService implements PlatformServerService {
    private final Plugin plugin;
    private final PlatformScheduler scheduler;
    private final Supplier<String> serverIdSupplier;

    public BukkitServerService(Plugin plugin, PlatformScheduler scheduler) {
        this(plugin, scheduler, () -> "standalone");
    }

    public BukkitServerService(Plugin plugin, PlatformScheduler scheduler, Supplier<String> serverIdSupplier) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.serverIdSupplier = serverIdSupplier == null ? () -> "standalone" : serverIdSupplier;
    }

    @Override
    public CompletionStage<ServerStatusItem> status(boolean includePerformance) {
        return scheduler.callGlobal(() -> new ServerStatusItem(
            serverIdSupplier.get(),
            plugin.getServer().getName(),
            ServerRole.STANDALONE,
            Status.success(),
            plugin.getServer().getName().toLowerCase(),
            plugin.getServer().getMinecraftVersion(),
            plugin.getServer().getOnlinePlayers().size(),
            plugin.getServer().getMaxPlayers()
        ));
    }

    @Override
    public ServerSnapshot snapshot() {
        return new ServerSnapshot(serverIdSupplier.get(), ServerRole.STANDALONE, plugin.getServer().getOnlinePlayers().size(), plugin.getServer().getMaxPlayers(), System.currentTimeMillis());
    }
}
