package io.github.railgun19457.astrbotadapter.transport;

import io.github.railgun19457.astrbotadapter.protocol.Frame;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

public interface TransportEndpoint {
    CompletionStage<Void> start();

    CompletionStage<Void> stop();

    CompletionStage<Void> send(Frame<?> frame);

    default CompletionStage<Frame<?>> sendRequest(Frame<?> frame, Duration timeout) {
        return java.util.concurrent.CompletableFuture.failedFuture(
            new UnsupportedOperationException("request/response is not supported by this endpoint")
        );
    }

    default int connectionCount() {
        return 0;
    }
}
