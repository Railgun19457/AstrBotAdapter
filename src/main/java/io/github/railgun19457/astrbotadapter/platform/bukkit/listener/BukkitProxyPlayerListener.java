package io.github.railgun19457.astrbotadapter.platform.bukkit.listener;

import io.github.railgun19457.astrbotadapter.communication.proxy.BukkitProxyClient;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Bukkit player listener for proxy mode.
 * Reports player join/quit events to the Velocity proxy via Plugin Messaging Channel.
 */
public class BukkitProxyPlayerListener implements Listener {

    private final BukkitProxyClient proxyClient;

    public BukkitProxyPlayerListener(BukkitProxyClient proxyClient) {
        this.proxyClient = proxyClient;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (proxyClient == null) {
            return;
        }

        Player player = event.getPlayer();

        // Try immediate authentication when first player joins (plugin messages need a player connection)
        if (!proxyClient.isAuthenticated()) {
            proxyClient.sendAuthRequest();

            // If auth succeeds quickly, report join immediately in current tick.
            if (proxyClient.isAuthenticated()) {
                reportJoinAndData(player);
            }
            return;
        }

        reportJoinAndData(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (proxyClient == null || !proxyClient.isAuthenticated()) {
            return;
        }

        Player player = event.getPlayer();
        proxyClient.reportPlayerQuit(
                player.getUniqueId(),
                player.getName(),
                player.getDisplayName(),
                "QUIT"
        );
    }

    private void reportJoinAndData(Player player) {
        proxyClient.reportPlayerJoin(
                player.getUniqueId(),
                player.getName(),
                player.getDisplayName()
        );

        // Also report detailed player data immediately.
        // (Avoid Bukkit scheduler APIs here for Folia compatibility.)
        if (player.isOnline()) {
            proxyClient.reportPlayerData(
                    new io.github.railgun19457.astrbotadapter.platform.bukkit.BukkitPlayer(player));
        }
    }
}
