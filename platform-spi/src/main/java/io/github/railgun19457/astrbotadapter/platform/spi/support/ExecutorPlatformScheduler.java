package io.github.railgun19457.astrbotadapter.platform.spi.support;

import io.github.railgun19457.astrbotadapter.platform.spi.LocationRef;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformScheduler;
import io.github.railgun19457.astrbotadapter.platform.spi.ScheduledTask;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class ExecutorPlatformScheduler implements PlatformScheduler {
    private final ScheduledExecutorService executor;

    public ExecutorPlatformScheduler(ScheduledExecutorService executor) {
        this.executor = executor;
    }

    @Override
    public void async(Runnable task) {
        executor.execute(task);
    }

    @Override
    public void global(Runnable task) {
        executor.execute(task);
    }

    @Override
    public void player(UUID playerId, Runnable task) {
        executor.execute(task);
    }

    @Override
    public void region(LocationRef location, Runnable task) {
        executor.execute(task);
    }

    @Override
    public <T> CompletionStage<T> callAsync(Supplier<T> task) {
        return call(task);
    }

    @Override
    public <T> CompletionStage<T> callGlobal(Supplier<T> task) {
        return call(task);
    }

    @Override
    public <T> CompletionStage<T> callPlayer(UUID playerId, Supplier<T> task) {
        return call(task);
    }

    @Override
    public <T> CompletionStage<T> callRegion(LocationRef location, Supplier<T> task) {
        return call(task);
    }

    @Override
    public ScheduledTask later(Duration delay, Runnable task) {
        return new FutureScheduledTask(executor.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS));
    }

    @Override
    public ScheduledTask repeat(Duration interval, Runnable task) {
        long intervalMillis = Math.max(1, interval.toMillis());
        return new FutureScheduledTask(executor.scheduleAtFixedRate(task, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS));
    }

    private <T> CompletionStage<T> call(Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                future.complete(task.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
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
