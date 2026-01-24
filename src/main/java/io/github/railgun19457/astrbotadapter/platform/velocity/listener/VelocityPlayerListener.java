package io.github.railgun19457.astrbotadapter.platform.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import io.github.railgun19457.astrbotadapter.service.notification.NotificationService;

import java.time.Duration;

/**
 * Velocity玩家监听器
 */
public class VelocityPlayerListener {

    private final Object plugin;
    private final ProxyServer proxy;
    private final NotificationService notificationService;

    public VelocityPlayerListener(Object plugin, ProxyServer proxy, NotificationService notificationService) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.notificationService = notificationService;
    }

    @Subscribe
    public void onPlayerJoin(PostLoginEvent event) {
        if (notificationService == null) {
            return;
        }

        Player player = event.getPlayer();
        
        // 延迟发送通知，确保玩家完全加入
        proxy.getScheduler().buildTask(plugin, () -> {
            if (player.isActive()) {
                notificationService.notifyPlayerJoin(
                        player.getUniqueId(),
                        player.getUsername(),
                        player.getUsername()
                );
            }
        }).delay(Duration.ofSeconds(1)).schedule();
    }

    @Subscribe
    public void onPlayerQuit(DisconnectEvent event) {
        if (notificationService == null) {
            return;
        }

        Player player = event.getPlayer();
        
        notificationService.notifyPlayerQuit(
                player.getUniqueId(),
                player.getUsername(),
                player.getUsername(),
                event.getLoginStatus().name()
        );
    }
}
