package io.github.railgun19457.astrbotadapter.core.config;

public record RateLimitConfig(int maxPendingRequests, int maxSendQueue, int maxFrameBytes, long heartbeatIntervalMillis, int heartbeatTimeoutsBeforeClose) {
}
