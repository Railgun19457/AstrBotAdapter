package io.github.railgun19457.astrbotadapter.protocol.payload;

public final class CommandPayloads {
    private CommandPayloads() {
    }

    public record CommandExecuteRequest(String dedupeKey, String command, Executor executor, CommonPayloads.Actor requestedBy, long requestTimeoutMs, long executionTimeoutMs, String mode) {
    }

    public record Executor(String type, String playerName, String playerUuid) {
    }

    public record CommandExecuteResponse(boolean accepted, boolean executed, String jobId, Boolean queued, Long estimatedDelayMs, Long durationMs, String output, Integer exitCode) {
    }

    public record CommandCompletedPayload(String jobId, String requestId, String serverId, boolean accepted, boolean executed, Long durationMs, String output, Integer exitCode, long completedAt) {
    }
}
