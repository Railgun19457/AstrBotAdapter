package io.github.railgun19457.astrbotadapter.protocol.payload;

public final class AiPayloads {
    private AiPayloads() {
    }

    public record AiGroupRequestPayload(CommonPayloads.PlayerRef player, CommonPayloads.TextMessage message, ConversationHint conversationHint) {
    }

    public record AiPrivateRequestPayload(CommonPayloads.PlayerRef player, CommonPayloads.TextMessage message, ConversationHint conversationHint) {
    }

    public record ConversationHint(String scope, String serverId, String playerUuid) {
    }

    public record AiReplyPayload(String dedupeKey, String replyToMessageId, Delivery delivery, CommonPayloads.TextMessage message) {
    }

    public record Delivery(String type, String playerUuid, String playerName) {
    }
}
