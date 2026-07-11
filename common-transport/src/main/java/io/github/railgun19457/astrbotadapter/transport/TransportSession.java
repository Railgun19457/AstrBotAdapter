package io.github.railgun19457.astrbotadapter.transport;

import io.github.railgun19457.astrbotadapter.protocol.Frame;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface TransportSession {
    String sessionId();

    SessionState state();

    Optional<SessionIdentity> authenticatedIdentity();

    CompletionStage<Void> send(Frame<?> frame);

    CompletionStage<Void> close(String reason);
}
