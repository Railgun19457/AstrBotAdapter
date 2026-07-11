package io.github.railgun19457.astrbotadapter.core.cluster;

import io.github.railgun19457.astrbotadapter.protocol.ServerRole;
import io.github.railgun19457.astrbotadapter.protocol.Status;
import io.github.railgun19457.astrbotadapter.protocol.payload.ServerPayloads;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BackendRegistry {
    private final Map<String, BackendNode> nodes = new ConcurrentHashMap<>();

    public void updateSnapshot(ServerPayloads.ServerSnapshotPayload snapshot) {
        ServerPayloads.ServerStatusItem item = new ServerPayloads.ServerStatusItem(
            snapshot.serverId(),
            snapshot.serverId(),
            snapshot.role() == null ? ServerRole.BACKEND : snapshot.role(),
            Status.success(),
            "backend",
            "",
            snapshot.onlineCount(),
            snapshot.maxPlayers(),
            snapshot.uptimeMs(),
            snapshot.memory(),
            snapshot.performance()
        );
        nodes.put(snapshot.serverId(), new BackendNode(snapshot.serverId(), item, System.currentTimeMillis()));
    }

    public List<ServerPayloads.ServerStatusItem> statusItems() {
        return nodes.values().stream().map(BackendNode::status).toList();
    }

    public boolean has(String serverId) {
        return nodes.containsKey(serverId);
    }

    public List<BackendNode> nodes() {
        return List.copyOf(nodes.values());
    }

    public record BackendNode(String serverId, ServerPayloads.ServerStatusItem status, long lastSeenAt) {
    }
}
