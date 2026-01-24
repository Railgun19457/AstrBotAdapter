package io.github.railgun19457.astrbotadapter.platform.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import io.github.railgun19457.astrbotadapter.service.chat.ChatService;
import io.github.railgun19457.astrbotadapter.service.forward.MessageForwardService;

/**
 * Velocity聊天监听器
 */
public class VelocityChatListener {

    private final ChatService chatService;
    private final MessageForwardService forwardService;

    public VelocityChatListener(ChatService chatService, MessageForwardService forwardService) {
        this.chatService = chatService;
        this.forwardService = forwardService;
    }

    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // 先检查AI聊天
        if (chatService != null && chatService.shouldTriggerChat(message)) {
            boolean handled = chatService.handlePlayerChat(
                    player.getUniqueId(),
                    player.getUsername(),
                    player.getUsername(),
                    message
            );
            if (handled) {
                return;
            }
        }

        // 检查消息转发
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
