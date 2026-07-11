package io.github.railgun19457.astrbotadapter.transport;

import io.github.railgun19457.astrbotadapter.protocol.Frame;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public final class DefaultRequestManager implements RequestManager {
    private final Function<Frame<?>, CompletionStage<Void>> sender;
    private final ScheduledExecutorService scheduler;
    private final int maxPending;
    private final ConcurrentHashMap<String, PendingRequest> pending = new ConcurrentHashMap<>();

    public DefaultRequestManager(Function<Frame<?>, CompletionStage<Void>> sender, ScheduledExecutorService scheduler, int maxPending) {
        this.sender = Objects.requireNonNull(sender, "sender");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.maxPending = Math.max(1, maxPending);
    }

    @Override
    public CompletionStage<Frame<?>> sendRequest(Frame<?> frame, Duration timeout) {
        Objects.requireNonNull(frame, "frame");
        if (frame.id() == null || frame.id().isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("request id is required"));
        }
        if (pending.size() >= maxPending) {
            return CompletableFuture.failedFuture(new IllegalStateException("max pending requests exceeded"));
        }

        CompletableFuture<Frame<?>> future = new CompletableFuture<>();
        Duration effectiveTimeout = timeout == null || timeout.isZero() || timeout.isNegative() ? Duration.ofSeconds(10) : timeout;
        ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> {
            PendingRequest removed = pending.remove(frame.id());
            if (removed != null) {
                removed.future().completeExceptionally(new TimeoutException("request timed out: " + frame.id()));
            }
        }, effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS);
        PendingRequest previous = pending.putIfAbsent(frame.id(), new PendingRequest(future, timeoutTask));
        if (previous != null) {
            timeoutTask.cancel(false);
            return CompletableFuture.failedFuture(new IllegalStateException("duplicate pending request id: " + frame.id()));
        }

        try {
            sender.apply(frame).whenComplete((ignored, throwable) -> {
                if (throwable != null) {
                    PendingRequest removed = pending.remove(frame.id());
                    if (removed != null) {
                        removed.timeoutTask().cancel(false);
                        removed.future().completeExceptionally(throwable);
                    }
                }
            });
        } catch (Throwable throwable) {
            PendingRequest removed = pending.remove(frame.id());
            if (removed != null) {
                removed.timeoutTask().cancel(false);
                removed.future().completeExceptionally(throwable);
            }
        }
        return future;
    }

    @Override
    public void resolveResponse(Frame<?> response) {
        if (response == null || response.replyTo() == null || response.replyTo().isBlank()) {
            return;
        }
        PendingRequest request = pending.remove(response.replyTo());
        if (request == null) {
            return;
        }
        request.timeoutTask().cancel(false);
        request.future().complete(response);
    }

    @Override
    public void cancelAll(String reason) {
        CancellationException exception = new CancellationException(reason == null ? "request manager closed" : reason);
        pending.forEach((ignored, request) -> {
            request.timeoutTask().cancel(false);
            request.future().completeExceptionally(exception);
        });
        pending.clear();
    }

    public int pendingCount() {
        return pending.size();
    }

    private record PendingRequest(CompletableFuture<Frame<?>> future, ScheduledFuture<?> timeoutTask) {
    }
}
