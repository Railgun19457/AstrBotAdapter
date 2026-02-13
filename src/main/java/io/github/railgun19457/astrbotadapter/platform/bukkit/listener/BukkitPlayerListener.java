package io.github.railgun19457.astrbotadapter.platform.bukkit.listener;

import io.github.railgun19457.astrbotadapter.service.notification.NotificationService;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Bukkit玩家监听器
 */
public class BukkitPlayerListener implements Listener {

    private final NotificationService notificationService;
    private final JavaPlugin plugin;

    public BukkitPlayerListener(JavaPlugin plugin, NotificationService notificationService) {
        this.plugin = plugin;
        this.notificationService = notificationService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (notificationService == null) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();

        // 延迟发送，确保玩家完全加入
        runDelayedTaskCompatible(
                player,
                () -> notificationService.notifyPlayerJoin(playerUuid, playerName, playerName),
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
                player.getName(),
                null // 离开原因（可从QuitReason获取，如果需要）
        );
    }

    @SuppressWarnings("unchecked")
    private void runDelayedTaskCompatible(Player player, Runnable task, long delayTicks) {
        // Folia：优先使用玩家区域调度器
        try {
            Method getSchedulerMethod = player.getClass().getMethod("getScheduler");
            Object entityScheduler = getSchedulerMethod.invoke(player);

            if (entityScheduler != null) {
                Method runDelayedMethod = entityScheduler.getClass().getMethod(
                        "runDelayed",
                        Plugin.class,
                        Consumer.class,
                        Runnable.class,
                        long.class
                );

                Consumer<Object> consumer = ignored -> task.run();
                runDelayedMethod.invoke(entityScheduler, plugin, consumer, null, delayTicks);
                return;
            }
        } catch (ReflectiveOperationException ignored) {
            // 非Folia环境或API签名不匹配，回退到Bukkit调度器
        }

        // Bukkit/Paper回退
        plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
    }
}
