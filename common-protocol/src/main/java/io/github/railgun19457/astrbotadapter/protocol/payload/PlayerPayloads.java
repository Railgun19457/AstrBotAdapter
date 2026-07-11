package io.github.railgun19457.astrbotadapter.protocol.payload;

import io.github.railgun19457.astrbotadapter.protocol.ServerRole;
import io.github.railgun19457.astrbotadapter.protocol.Status;

import java.util.List;

public final class PlayerPayloads {
    private PlayerPayloads() {
    }

    public record PlayerJoinPayload(CommonPayloads.PlayerRef player, int onlineCount, int maxPlayers, long joinedAt) {
    }

    public record PlayerLeavePayload(CommonPayloads.PlayerRef player, int onlineCount, int maxPlayers, String reason, long leftAt) {
    }

    public record PlayerListRequest(boolean includeDetails, boolean includeRecentOffline) {
    }

    public record PlayerListResponse(List<PlayerServerItem> servers, List<CommonPayloads.AggregateErrorItem> errors, long generatedAt) {
    }

    public record PlayerServerItem(String serverId, String displayName, ServerRole role, Status status, int onlineCount, List<PlayerItem> players) {
    }

    public record PlayerItem(String uuid, String name, String displayName, String serverId, String world, String gameMode, Integer pingMs) {
    }

    public record PlayerDetailRequest(PlayerQuery query, boolean includeHistory) {
    }

    public record PlayerQuery(String type, String value) {
    }

    public record PlayerDetailResponse(PlayerDetailItem player) {
    }

    public record PlayerDetailItem(
        String uuid,
        String name,
        String displayName,
        boolean online,
        String serverId,
        String world,
        String gameMode,
        Integer pingMs,
        Double health,
        Double maxHealth,
        Integer foodLevel,
        Integer level,
        Double exp,
        Boolean isOperator,
        Boolean allowFlight,
        CommonPayloads.Location location,
        Long firstPlayedAt,
        Long lastPlayedAt,
        Long onlineDurationMs
    ) {
    }
}
