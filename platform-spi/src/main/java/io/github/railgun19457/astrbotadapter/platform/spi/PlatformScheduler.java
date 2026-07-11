package io.github.railgun19457.astrbotadapter.platform.spi;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public interface PlatformScheduler {
    void async(Runnable task);

    void global(Runnable task);

    void player(UUID playerId, Runnable task);

    void region(LocationRef location, Runnable task);

    <T> CompletionStage<T> callAsync(Supplier<T> task);

    <T> CompletionStage<T> callGlobal(Supplier<T> task);

    <T> CompletionStage<T> callPlayer(UUID playerId, Supplier<T> task);

    <T> CompletionStage<T> callRegion(LocationRef location, Supplier<T> task);

    ScheduledTask later(Duration delay, Runnable task);

    ScheduledTask repeat(Duration interval, Runnable task);
}
