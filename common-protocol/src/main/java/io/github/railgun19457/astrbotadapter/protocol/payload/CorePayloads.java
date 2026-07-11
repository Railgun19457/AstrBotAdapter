package io.github.railgun19457.astrbotadapter.protocol.payload;

import java.util.List;

public final class CorePayloads {
    private CorePayloads() {
    }

    public record AckPayload(boolean received) {
    }

    public record HeartbeatPayload(String sessionId, Long serverTime) {
    }

    public record GoodbyePayload(String reason, String message, Long retryAfterMs) {
    }

    public record CapabilitiesUpdatedPayload(String serverId, List<String> capabilities, long updatedAt) {
    }
}
