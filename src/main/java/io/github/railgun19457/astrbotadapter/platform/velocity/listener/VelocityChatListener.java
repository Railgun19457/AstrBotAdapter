package io.github.railgun19457.astrbotadapter.platform.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import io.github.railgun19457.astrbotadapter.core.util.PlaceholderUtil;
import io.github.railgun19457.astrbotadapter.service.chat.ChatService;
import io.github.railgun19457.astrbotadapter.service.forward.MessageForwardService;
import net.kyori.adventure.text.Component;

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
            // 检查是否为私聊模式
            boolean isPrivate = chatService.isPrivateChatTrigger(message);
            
            boolean handled = chatService.handlePlayerChat(
                    player.getUniqueId(),
                    player.getUsername(),
                    player.getUsername(),
                    message
            );
            if (handled) {
                // 私聊模式：取消消息发送，模拟显示给玩家自己
                if (isPrivate) {
                    event.setResult(PlayerChatEvent.ChatResult.denied());
                    // 使用配置的格式模拟显示消息
                    String echoFormat = chatService.getConfig().getPrivateChatEchoFormat();
                    String echoMessage = PlaceholderUtil.replace(echoFormat, 
                            "player", player.getUsername(),
                            "message", message);
                    player.sendMessage(Component.text(echoMessage));
                }
                // 群聊模式：不取消，让其他玩家也能看到原始消息
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
