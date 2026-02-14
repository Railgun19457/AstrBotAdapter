package io.github.railgun19457.astrbotadapter.platform.bukkit.listener;

import io.github.railgun19457.astrbotadapter.communication.proxy.BukkitProxyClient;
import io.github.railgun19457.astrbotadapter.core.config.PluginConfig;
import io.github.railgun19457.astrbotadapter.core.util.PlaceholderUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Bukkit chat listener for proxy mode.
 * Handles AI chat locally (prefix detection, private chat cancellation, echo display),
 * then forwards the processed request to the Velocity proxy via Plugin Messaging Channel.
 * Also reports raw chat messages for message forwarding.
 */
public class BukkitProxyChatListener implements Listener {

    private final BukkitProxyClient proxyClient;
    private final PluginConfig config;

    public BukkitProxyChatListener(BukkitProxyClient proxyClient, PluginConfig config) {
        this.proxyClient = proxyClient;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (proxyClient == null || !proxyClient.isAuthenticated()) {
            return;
        }

        Player player = event.getPlayer();
        String message = event.getMessage();

        // Handle AI chat locally on the backend
        if (handleAiChat(event, player, message)) {
            return; // AI chat handled, don't report as normal chat
        }

        // Report raw chat message to proxy for message forwarding
        proxyClient.reportChatMessage(
                player.getUniqueId(),
                player.getName(),
                player.getDisplayName(),
                message
        );
    }

    /**
     * Handle AI chat triggers locally.
     * Private chat: cancel the event and echo the message to the player.
     * Group chat: let the event through (other players see it).
     * In both cases, send the AI request to the proxy for forwarding to Astrbot.
     *
     * @return true if the message was an AI chat trigger
     */
    private boolean handleAiChat(AsyncPlayerChatEvent event, Player player, String message) {
        // Check private chat prefix first
        if (config.isPrivateChatEnabled()) {
            String privatePrefix = config.getPrivateChatPrefix();
            if (privatePrefix != null && !privatePrefix.isEmpty() && message.startsWith(privatePrefix)) {
                String content = message.substring(privatePrefix.length()).trim();
                if (!content.isEmpty()) {
                    // Cancel the event so other players don't see the private AI message
                    event.setCancelled(true);

                    // Echo the message to the sender
                    String echoFormat = config.getPrivateChatEchoFormat();
                    String echoMessage = PlaceholderUtil.replace(echoFormat,
                            "player", player.getDisplayName(),
                            "message", message);
                    player.sendMessage(echoMessage);

                    // Report AI chat request to proxy for forwarding to Astrbot
                    proxyClient.reportAiChatRequest(
                            player.getUniqueId(),
                            player.getName(),
                            player.getDisplayName(),
                            content,
                            "PRIVATE"
                    );
                    return true;
                }
            }
        }

        // Check group chat prefix
        if (config.isGroupChatEnabled()) {
            String groupPrefix = config.getGroupChatPrefix();
            if (groupPrefix != null && !groupPrefix.isEmpty() && message.startsWith(groupPrefix)) {
                String content = message.substring(groupPrefix.length()).trim();
                if (!content.isEmpty()) {
                    // Don't cancel the event - other players should see the message
                    // Report AI chat request to proxy for forwarding to Astrbot
                    proxyClient.reportAiChatRequest(
                            player.getUniqueId(),
                            player.getName(),
                            player.getDisplayName(),
                            content,
                            "GROUP"
                    );
                    return true;
                }
            }
        }

        return false;
    }
}
