package io.github.railgun19457.astrbotadapter.platform.folia;

import io.github.railgun19457.astrbotadapter.platform.spi.LocationRef;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformScheduler;
import io.github.railgun19457.astrbotadapter.platform.spi.ScheduledTask;
import io.github.railgun19457.astrbotadapter.platform.spi.support.ExecutorPlatformScheduler;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class FoliaPlatformScheduler implements PlatformScheduler {
    private final Plugin plugin;
    private final ExecutorPlatformScheduler fallback;

    FoliaPlatformScheduler(Plugin plugin, ScheduledExecutorService fallbackExecutor) {
        this.plugin = plugin;
        this.fallback = new ExecutorPlatformScheduler(fallbackExecutor);
    }

    @Override
    public void async(Runnable task) {
        fallback.async(task);
    }

    @Override
    public void global(Runnable task) {
        if (!invokeGlobal("run", new Class<?>[]{Plugin.class, Consumer.class}, plugin, consumer(task))) {
            fallback.global(task);
        }
    }

    @Override
    public void player(UUID playerId, Runnable task) {
        if (playerId == null || !invokeEntity(playerId, task)) {
            global(task);
        }
    }

    @Override
    public void region(LocationRef location, Runnable task) {
        if (location == null || !invokeRegion(location, task)) {
            global(task);
        }
    }

    @Override
    public <T> CompletionStage<T> callAsync(Supplier<T> task) {
        return fallback.callAsync(task);
    }

    @Override
    public <T> CompletionStage<T> callGlobal(Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        global(() -> complete(future, task));
        return future;
    }

    @Override
    public <T> CompletionStage<T> callPlayer(UUID playerId, Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        player(playerId, () -> complete(future, task));
        return future;
    }

    @Override
    public <T> CompletionStage<T> callRegion(LocationRef location, Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        region(location, () -> complete(future, task));
        return future;
    }

    @Override
    public ScheduledTask later(Duration delay, Runnable task) {
        Object scheduled = invokeGlobalReturning("runDelayed", new Class<?>[]{Plugin.class, Consumer.class, long.class}, plugin, consumer(task), toTicks(delay));
        return scheduled == null ? fallback.later(delay, task) : new ReflectedScheduledTask(scheduled);
    }

    @Override
    public ScheduledTask repeat(Duration interval, Runnable task) {
        long ticks = toTicks(interval);
        Object scheduled = invokeGlobalReturning("runAtFixedRate", new Class<?>[]{Plugin.class, Consumer.class, long.class, long.class}, plugin, consumer(task), ticks, ticks);
        return scheduled == null ? fallback.repeat(interval, task) : new ReflectedScheduledTask(scheduled);
    }

    private Consumer<Object> consumer(Runnable task) {
        return ignored -> task.run();
    }

    private boolean invokeGlobal(String method, Class<?>[] parameterTypes, Object... args) {
        return invokeGlobalReturning(method, parameterTypes, args) != null;
    }

    private boolean invokeEntity(UUID playerId, Runnable task) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return false;
        }
        try {
            Object scheduler = player.getClass().getMethod("getScheduler").invoke(player);
            Method run = scheduler.getClass().getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            run.invoke(scheduler, plugin, consumer(task), null);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    private boolean invokeRegion(LocationRef location, Runnable task) {
        World world = Bukkit.getWorld(location.world());
        if (world == null) {
            return false;
        }
        int chunkX = floorToChunk(location.x());
        int chunkZ = floorToChunk(location.z());
        Object scheduled = invokeRegionReturning("run", new Class<?>[]{Plugin.class, World.class, int.class, int.class, Consumer.class}, plugin, world, chunkX, chunkZ, consumer(task));
        return scheduled != null;
    }

    private Object invokeGlobalReturning(String method, Class<?>[] parameterTypes, Object... args) {
        try {
            Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            Method target = scheduler.getClass().getMethod(method, parameterTypes);
            return target.invoke(scheduler, args);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private Object invokeRegionReturning(String method, Class<?>[] parameterTypes, Object... args) {
        try {
            Object scheduler = Bukkit.class.getMethod("getRegionScheduler").invoke(null);
            Method target = scheduler.getClass().getMethod(method, parameterTypes);
            return target.invoke(scheduler, args);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private int floorToChunk(double coordinate) {
        return (int) Math.floor(coordinate) >> 4;
    }

    private long toTicks(Duration duration) {
        return Math.max(1, duration.toMillis() / 50L);
    }

    private <T> void complete(CompletableFuture<T> future, Supplier<T> supplier) {
        try {
            future.complete(supplier.get());
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
    }

    private record ReflectedScheduledTask(Object task) implements ScheduledTask {
        @Override
        public void cancel() {
            try {
                task.getClass().getMethod("cancel").invoke(task);
            } catch (ReflectiveOperationException ignored) {
            }
        }

        @Override
        public boolean cancelled() {
            try {
                Object result = task.getClass().getMethod("isCancelled").invoke(task);
                return result instanceof Boolean cancelled && cancelled;
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }
    }
}
