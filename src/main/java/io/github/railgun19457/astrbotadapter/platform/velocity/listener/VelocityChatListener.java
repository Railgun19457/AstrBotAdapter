package io.github.railgun19457.astrbotadapter.platform.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import io.github.railgun19457.astrbotadapter.service.forward.MessageForwardService;

/**
 * Velocity chat listener.
 * The proxy only handles message forwarding.
 * AI chat (prefix detection, private chat cancellation, echo) is handled
 * by the backend servers, since only the backend can properly cancel
 * Bukkit/Folia chat events.
 */
public class VelocityChatListener {

    private final MessageForwardService forwardService;

    public VelocityChatListener(MessageForwardService forwardService) {
        this.forwardService = forwardService;
    }

    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // Only handle message forwarding on the proxy
        // AI chat handling is delegated to backend servers
        if (forwardService != null && forwardService.shouldForward(message)) {
            forwardService.handlePlayerMessage(
                    player.getUniqueId(),
                    player.getUsername(),
                    player.getUsername(),
                    message
            );
        }
    }
}
