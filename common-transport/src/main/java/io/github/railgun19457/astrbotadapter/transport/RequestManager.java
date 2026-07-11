package io.github.railgun19457.astrbotadapter.transport;

import io.github.railgun19457.astrbotadapter.protocol.Frame;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

public interface RequestManager {
    CompletionStage<Frame<?>> sendRequest(Frame<?> frame, Duration timeout);

    void resolveResponse(Frame<?> response);

    void cancelAll(String reason);
}
