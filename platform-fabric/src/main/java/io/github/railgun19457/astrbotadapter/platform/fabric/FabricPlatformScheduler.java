package io.github.railgun19457.astrbotadapter.platform.fabric;

import io.github.railgun19457.astrbotadapter.platform.spi.LocationRef;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformScheduler;
import io.github.railgun19457.astrbotadapter.platform.spi.ScheduledTask;
import net.minecraft.server.MinecraftServer;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

final class FabricPlatformScheduler implements PlatformScheduler {
    private final MinecraftServer server;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "AstrBotAdapter-Fabric-Core");
        thread.setDaemon(true);
        return thread;
    });

    FabricPlatformScheduler(MinecraftServer server) {
        this.server = server;
    }

    void shutdown() {
        executor.shutdownNow();
    }

    @Override
    public void async(Runnable task) {
        executor.execute(task);
    }

    @Override
    public void global(Runnable task) {
        if (server.isSameThread()) {
            task.run();
        } else {
            server.execute(task);
        }
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
        ScheduledFuture<?> future = executor.schedule(() -> global(task), Math.max(1L, delay.toMillis()), TimeUnit.MILLISECONDS);
        return new FutureScheduledTask(future);
    }

    @Override
    public ScheduledTask repeat(Duration interval, Runnable task) {
        long millis = Math.max(1L, interval.toMillis());
        ScheduledFuture<?> future = executor.scheduleAtFixedRate(() -> global(task), millis, millis, TimeUnit.MILLISECONDS);
        return new FutureScheduledTask(future);
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

    @FunctionalInterface
    private interface TaskConsumer {
        void schedule(Runnable task);
    }

    private record FutureScheduledTask(ScheduledFuture<?> future) implements ScheduledTask {
        @Override
        public void cancel() {
            future.cancel(false);
        }

        @Override
        public boolean cancelled() {
            return future.isCancelled();
        }
    }
}
