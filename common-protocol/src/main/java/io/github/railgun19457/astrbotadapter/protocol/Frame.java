package io.github.railgun19457.astrbotadapter.protocol;

public record Frame<TPayload>(
    int v,
    String id,
    FrameType type,
    String op,
    long ts,
    long seq,
    Route route,
    Origin origin,
    Target target,
    String replyTo,
    Status status,
    TPayload payload
) {
}
