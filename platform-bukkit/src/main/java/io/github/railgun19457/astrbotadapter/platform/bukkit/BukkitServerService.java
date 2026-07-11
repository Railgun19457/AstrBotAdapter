package io.github.railgun19457.astrbotadapter.platform.bukkit;

import io.github.railgun19457.astrbotadapter.platform.spi.PlatformScheduler;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformServerService;
import io.github.railgun19457.astrbotadapter.platform.spi.ServerSnapshot;
import io.github.railgun19457.astrbotadapter.platform.spi.ServerStatusItem;
import io.github.railgun19457.astrbotadapter.protocol.ServerRole;
import io.github.railgun19457.astrbotadapter.protocol.Status;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletionStage;

public final class BukkitServerService implements PlatformServerService {
    private final Plugin plugin;
    private final PlatformScheduler scheduler;

    public BukkitServerService(Plugin plugin, PlatformScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    @Override
    public CompletionStage<ServerStatusItem> status(boolean includePerformance) {
        return scheduler.callGlobal(() -> new ServerStatusItem(
            "standalone",
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
        return new ServerSnapshot("standalone", ServerRole.STANDALONE, plugin.getServer().getOnlinePlayers().size(), plugin.getServer().getMaxPlayers(), System.currentTimeMillis());
    }
}
