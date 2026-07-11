package io.github.railgun19457.astrbotadapter.protocol.payload;

public final class ConfigPayloads {
    private ConfigPayloads() {
    }

    public record ConfigSyncPayload(String revision, AiConfig ai, ChatConfig chat, Permissions permissions) {
    }

    public record AiConfig(boolean groupEnabled, String groupPrefix, boolean privateEnabled, String privatePrefix) {
    }

    public record ChatConfig(boolean forwardEnabled, String forwardPrefix) {
    }

    public record Permissions(boolean reportStatus, boolean reportPlayers, boolean executeCommand, boolean sendPrivateMessage, boolean queryLogs) {
    }
}
