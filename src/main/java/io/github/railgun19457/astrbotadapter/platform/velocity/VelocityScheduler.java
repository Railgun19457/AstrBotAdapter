package io.github.railgun19457.astrbotadapter.platform.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import io.github.railgun19457.astrbotadapter.platform.common.CommonScheduler;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Velocity调度器包装类
 */
public class VelocityScheduler implements CommonScheduler {

    private final Object plugin;
    private final ProxyServer proxy;
    private final AtomicInteger taskIdCounter = new AtomicInteger(0);
    private final Map<Integer, ScheduledTask> tasks = new ConcurrentHashMap<>();

    public VelocityScheduler(Object plugin, ProxyServer proxy) {
        this.plugin = plugin;
        this.proxy = proxy;
    }

    @Override
    public void runSync(Runnable task) {
        // Velocity没有主线程概念，所有任务都是异步的
        proxy.getScheduler().buildTask(plugin, task).schedule();
    }

    @Override
    public void runAsync(Runnable task) {
        proxy.getScheduler().buildTask(plugin, task).schedule();
    }

    @Override
    public void runLater(Runnable task, long delayTicks) {
        long delayMs = delayTicks * 50; // 1 tick = 50ms
        proxy.getScheduler().buildTask(plugin, task)
                .delay(Duration.ofMillis(delayMs))
                .schedule();
    }

    @Override
    public void runLaterAsync(Runnable task, long delayTicks) {
        runLater(task, delayTicks);
    }

    @Override
    public int runTimer(Runnable task, long delayTicks, long periodTicks) {
        long delayMs = delayTicks * 50;
        long periodMs = periodTicks * 50;
        
        int taskId = taskIdCounter.incrementAndGet();
        ScheduledTask scheduledTask = proxy.getScheduler().buildTask(plugin, task)
                .delay(Duration.ofMillis(delayMs))
                .repeat(Duration.ofMillis(periodMs))
                .schedule();
        
        tasks.put(taskId, scheduledTask);
        return taskId;
    }

    @Override
    public int runTimerAsync(Runnable task, long delayTicks, long periodTicks) {
        return runTimer(task, delayTicks, periodTicks);
    }

    @Override
    public void cancelTask(int taskId) {
        ScheduledTask task = tasks.remove(taskId);
        if (task != null) {
            task.cancel();
        }
    }

    @Override
    public void cancelAll() {
        for (ScheduledTask task : tasks.values()) {
            task.cancel();
        }
        tasks.clear();
    }

    @Override
    public boolean isMainThread() {
        // Velocity没有主线程
        return false;
    }
}
