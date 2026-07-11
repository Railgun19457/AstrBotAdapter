package io.github.railgun19457.astrbotadapter.platform.bukkit;

import io.github.railgun19457.astrbotadapter.platform.spi.LocationRef;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformScheduler;
import io.github.railgun19457.astrbotadapter.platform.spi.ScheduledTask;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

final class BukkitPlatformScheduler implements PlatformScheduler {
    private final Plugin plugin;

    BukkitPlatformScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void async(Runnable task) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public void global(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    @Override
    public void player(UUID playerId, Runnable task) {
        global(task);
    }

    @Override
    public void region(LocationRef location, Runnable task) {
        global(task);
    }

    @Override
    public <T> CompletionStage<T> callAsync(Supplier<T> task) {
        return call(this::async, task);
    }

    @Override
    public <T> CompletionStage<T> callGlobal(Supplier<T> task) {
        return call(this::global, task);
    }

    @Override
    public <T> CompletionStage<T> callPlayer(UUID playerId, Supplier<T> task) {
        return callGlobal(task);
    }

    @Override
    public <T> CompletionStage<T> callRegion(LocationRef location, Supplier<T> task) {
        return callGlobal(task);
    }

    @Override
    public ScheduledTask later(Duration delay, Runnable task) {
        return new BukkitScheduledTask(plugin.getServer().getScheduler().runTaskLater(plugin, task, toTicks(delay)));
    }

    @Override
    public ScheduledTask repeat(Duration interval, Runnable task) {
        long ticks = toTicks(interval);
        return new BukkitScheduledTask(plugin.getServer().getScheduler().runTaskTimer(plugin, task, ticks, ticks));
    }

    private <T> CompletionStage<T> call(TaskConsumer scheduler, Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        scheduler.schedule(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    private long toTicks(Duration duration) {
        return Math.max(1, duration.toMillis() / 50L);
    }

    @FunctionalInterface
    private interface TaskConsumer {
        void schedule(Runnable task);
    }

    private record BukkitScheduledTask(BukkitTask task) implements ScheduledTask {
        @Override
        public void cancel() {
            task.cancel();
        }

        @Override
        public boolean cancelled() {
            return task.isCancelled();
        }
    }
}
