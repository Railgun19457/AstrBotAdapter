package io.github.railgun19457.astrbotadapter.platform.folia;

import io.github.railgun19457.astrbotadapter.platform.common.CommonScheduler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

/**
 * Folia调度器包装类
 * Folia使用区域化线程，需要特殊处理
 */
public class FoliaScheduler implements CommonScheduler {

    private final Plugin plugin;

    public FoliaScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runSync(Runnable task) {
        // Folia中，全局区域任务用于非位置相关的操作
        Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
    }

    @Override
    public void runAsync(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
    }

    @Override
    public void runLater(Runnable task, long delayTicks) {
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayTicks);
    }

    @Override
    public void runLaterAsync(Runnable task, long delayTicks) {
        long delayMs = delayTicks * 50; // 1 tick = 50ms
        Bukkit.getAsyncScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public int runTimer(Runnable task, long delayTicks, long periodTicks) {
        // Folia的定时任务返回ScheduledTask，我们需要追踪它
        // 这里简化处理，返回一个模拟的任务ID
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), delayTicks, periodTicks);
        return -1; // Folia不使用整数任务ID
    }

    @Override
    public int runTimerAsync(Runnable task, long delayTicks, long periodTicks) {
        long delayMs = delayTicks * 50;
        long periodMs = periodTicks * 50;
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), delayMs, periodMs, TimeUnit.MILLISECONDS);
        return -1;
    }

    @Override
    public void cancelTask(int taskId) {
        // Folia使用不同的取消机制
        // 由于我们没有跟踪ScheduledTask，这里无法取消
        plugin.getLogger().warning("Folia调度器不支持通过ID取消任务");
    }

    @Override
    public void cancelAll() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
    }

    @Override
    public boolean isMainThread() {
        // Folia中没有单一主线程概念
        return Bukkit.isGlobalTickThread();
    }
}
