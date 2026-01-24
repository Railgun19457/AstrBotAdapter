package io.github.railgun19457.astrbotadapter.platform.bukkit;

import io.github.railgun19457.astrbotadapter.platform.common.CommonScheduler;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;

/**
 * Bukkit调度器包装类
 */
public class BukkitSchedulerWrapper implements CommonScheduler {

    private final Plugin plugin;
    private final BukkitScheduler scheduler;

    public BukkitSchedulerWrapper(Plugin plugin) {
        this.plugin = plugin;
        this.scheduler = plugin.getServer().getScheduler();
    }

    @Override
    public void runSync(Runnable task) {
        scheduler.runTask(plugin, task);
    }

    @Override
    public void runAsync(Runnable task) {
        scheduler.runTaskAsynchronously(plugin, task);
    }

    @Override
    public void runLater(Runnable task, long delayTicks) {
        scheduler.runTaskLater(plugin, task, delayTicks);
    }

    @Override
    public void runLaterAsync(Runnable task, long delayTicks) {
        scheduler.runTaskLaterAsynchronously(plugin, task, delayTicks);
    }

    @Override
    public int runTimer(Runnable task, long delayTicks, long periodTicks) {
        return scheduler.runTaskTimer(plugin, task, delayTicks, periodTicks).getTaskId();
    }

    @Override
    public int runTimerAsync(Runnable task, long delayTicks, long periodTicks) {
        return scheduler.runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks).getTaskId();
    }

    @Override
    public void cancelTask(int taskId) {
        scheduler.cancelTask(taskId);
    }

    @Override
    public void cancelAll() {
        scheduler.cancelTasks(plugin);
    }

    @Override
    public boolean isMainThread() {
        return plugin.getServer().isPrimaryThread();
    }
}
