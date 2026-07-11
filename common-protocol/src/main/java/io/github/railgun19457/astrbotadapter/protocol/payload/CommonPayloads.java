package io.github.railgun19457.astrbotadapter.protocol.payload;

import io.github.railgun19457.astrbotadapter.protocol.ServerRole;
import io.github.railgun19457.astrbotadapter.protocol.Status;

import java.util.List;
import java.util.Map;

public final class CommonPayloads {
    private CommonPayloads() {
    }

    public record Actor(String type, String platform, String id, String name, String displayName, Boolean isOperator) {
    }

    public record TextMessage(String messageId, String content, String plainText, String format, List<Object> mentions, Long createdAt) {
        public static TextMessage plain(String content, long createdAt) {
            return new TextMessage(null, content, content, "plain", List.of(), createdAt);
        }
    }

    public record ServerRef(String serverId, String displayName, ServerRole role) {
    }

    public record PlayerRef(String uuid, String name, String displayName, String serverId) {
    }

    public record AggregateErrorItem(String serverId, ServerRole role, Status status) {
    }

    public record Location(String world, double x, double y, double z, float yaw, float pitch) {
    }

    public record AnyPayload(Map<String, Object> values) {
    }
}
