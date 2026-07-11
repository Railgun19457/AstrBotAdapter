package io.github.railgun19457.astrbotadapter.core.config;

public record WebSocketConfig(boolean enabled, String host, int port, String path, String url) {
}
