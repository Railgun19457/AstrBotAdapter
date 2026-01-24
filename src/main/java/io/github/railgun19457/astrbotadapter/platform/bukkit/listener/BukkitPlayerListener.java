package io.github.railgun19457.astrbotadapter.platform.bukkit.listener;

import io.github.railgun19457.astrbotadapter.service.notification.NotificationService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Bukkit玩家监听器
 */
public class BukkitPlayerListener implements Listener {

    private final NotificationService notificationService;

    public BukkitPlayerListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (notificationService == null) {
            return;
        }

        Player player = event.getPlayer();
        
        // 延迟发送，确保玩家完全加入
        player.getServer().getScheduler().runTaskLaterAsynchronously(
                player.getServer().getPluginManager().getPlugins()[0], // 获取当前插件
                () -> notificationService.notifyPlayerJoin(
                        player.getUniqueId(),
                        player.getName(),
                        player.getDisplayName()
                ),
                20L // 延迟1秒
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (notificationService == null) {
            return;
        }

        Player player = event.getPlayer();
        
        notificationService.notifyPlayerQuit(
                player.getUniqueId(),
                player.getName(),
                player.getDisplayName(),
                null // 离开原因（可从QuitReason获取，如果需要）
        );
    }
}
