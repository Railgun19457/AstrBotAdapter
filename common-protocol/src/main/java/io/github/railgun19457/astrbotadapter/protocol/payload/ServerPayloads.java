package io.github.railgun19457.astrbotadapter.protocol.payload;

import io.github.railgun19457.astrbotadapter.protocol.ServerRole;
import io.github.railgun19457.astrbotadapter.protocol.Status;

import java.util.List;

public final class ServerPayloads {
    private ServerPayloads() {
    }

    public record ServerStatusRequest(List<String> serverIds, boolean includePerformance) {
    }

    public record ServerStatusResponse(List<ServerStatusItem> servers, List<CommonPayloads.AggregateErrorItem> errors, long generatedAt) {
    }

    public record ServerStatusItem(
        String serverId,
        String displayName,
        ServerRole role,
        Status status,
        String platform,
        String version,
        int onlineCount,
        int maxPlayers,
        Long uptimeMs,
        Memory memory,
        Performance performance
    ) {
    }

    public record Memory(long usedMb, long maxMb) {
    }

    public record Performance(Double tps, Double mspt) {
    }

    public record ServerSnapshotPayload(String serverId, ServerRole role, int onlineCount, int maxPlayers, Long uptimeMs, Memory memory, Performance performance, long reportedAt) {
    }
}
