package io.github.railgun19457.astrbotadapter.platform.bukkit.listener;

import io.github.railgun19457.astrbotadapter.service.chat.ChatService;
import io.github.railgun19457.astrbotadapter.service.forward.MessageForwardService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Bukkit聊天监听器
 */
public class BukkitChatListener implements Listener {

    private final ChatService chatService;
    private final MessageForwardService forwardService;

    public BukkitChatListener(ChatService chatService, MessageForwardService forwardService) {
        this.chatService = chatService;
        this.forwardService = forwardService;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // 先检查AI聊天
        if (chatService != null && chatService.shouldTriggerChat(message)) {
            boolean handled = chatService.handlePlayerChat(
                    player.getUniqueId(),
                    player.getName(),
                    player.getDisplayName(),
                    message
            );
            if (handled) {
                // AI聊天消息不取消，让其他玩家也能看到原始消息
                return;
            }
        }

        // 检查消息转发
        if (forwardService != null && forwardService.shouldForward(message)) {
            forwardService.handlePlayerMessage(
                    player.getUniqueId(),
                    player.getName(),
                    player.getDisplayName(),
                    message
            );
        }
    }
}
