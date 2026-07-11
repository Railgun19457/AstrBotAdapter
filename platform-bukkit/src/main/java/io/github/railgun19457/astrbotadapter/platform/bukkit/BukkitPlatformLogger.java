package io.github.railgun19457.astrbotadapter.platform.bukkit;

import io.github.railgun19457.astrbotadapter.platform.spi.PlatformLogger;
import org.bukkit.plugin.Plugin;

final class BukkitPlatformLogger implements PlatformLogger {
    private final Plugin plugin;

    BukkitPlatformLogger(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void info(String message) {
        plugin.getLogger().info(message);
    }

    @Override
    public void warn(String message) {
        plugin.getLogger().warning(message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        plugin.getLogger().severe(message + ": " + throwable.getMessage());
    }
}
