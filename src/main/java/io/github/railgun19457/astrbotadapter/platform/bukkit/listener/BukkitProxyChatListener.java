package io.github.railgun19457.astrbotadapter.platform.bukkit.listener;

import io.github.railgun19457.astrbotadapter.communication.proxy.BukkitProxyClient;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Bukkit chat listener for proxy mode.
 * Instead of sending chat to Astrbot directly, reports it to the Velocity proxy
 * via Plugin Messaging Channel.
 */
public class BukkitProxyChatListener implements Listener {

    private final BukkitProxyClient proxyClient;

    public BukkitProxyChatListener(BukkitProxyClient proxyClient) {
        this.proxyClient = proxyClient;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (proxyClient == null || !proxyClient.isAuthenticated()) {
            return;
        }

        Player player = event.getPlayer();
        proxyClient.reportChatMessage(
                player.getUniqueId(),
                player.getName(),
                player.getDisplayName(),
                event.getMessage()
        );
    }
}
