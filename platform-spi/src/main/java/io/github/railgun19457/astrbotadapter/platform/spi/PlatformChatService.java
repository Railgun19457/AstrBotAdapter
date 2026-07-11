package io.github.railgun19457.astrbotadapter.platform.spi;

import java.util.concurrent.CompletionStage;

public interface PlatformChatService {
    CompletionStage<Void> broadcast(TextOutboundMessage message);

    CompletionStage<Void> sendPrivate(PlayerSelector player, TextOutboundMessage message);
}
