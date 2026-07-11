package io.github.railgun19457.astrbotadapter.protocol.payload;

public final class ChatPayloads {
    private ChatPayloads() {
    }

    public record ChatPlayerPayload(CommonPayloads.PlayerRef player, CommonPayloads.TextMessage message, String world, String source) {
    }

    public record ConversationRef(String platform, String type, String id) {
    }

    public record BridgeInfo(String originPlatform, String originConversationId, boolean suppressEcho) {
    }

    public record ChatExternalPayload(String dedupeKey, CommonPayloads.Actor sender, ConversationRef conversation, BridgeInfo bridge, CommonPayloads.TextMessage message) {
    }

    public record ChatPrivatePayload(String dedupeKey, PlayerTarget player, CommonPayloads.TextMessage message) {
    }

    public record PlayerTarget(String uuid, String name, String serverId) {
    }
}
