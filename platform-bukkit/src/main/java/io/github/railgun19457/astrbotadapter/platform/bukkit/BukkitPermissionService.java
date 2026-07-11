package io.github.railgun19457.astrbotadapter.platform.bukkit;

import io.github.railgun19457.astrbotadapter.platform.spi.PlatformPermissionService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public final class BukkitPermissionService implements PlatformPermissionService {
    private final Plugin plugin;

    public BukkitPermissionService(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean hasPermission(String subjectId, String permission) {
        try {
            Player player = plugin.getServer().getPlayer(UUID.fromString(subjectId));
            return player != null && player.hasPermission(permission);
        } catch (IllegalArgumentException exception) {
            Player player = plugin.getServer().getPlayerExact(subjectId);
            return player != null && player.hasPermission(permission);
        }
    }
}
