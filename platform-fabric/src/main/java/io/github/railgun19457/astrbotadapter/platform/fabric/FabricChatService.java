package io.github.railgun19457.astrbotadapter.platform.fabric;

import io.github.railgun19457.astrbotadapter.platform.spi.PlatformChatService;
import io.github.railgun19457.astrbotadapter.platform.spi.PlayerSelector;
import io.github.railgun19457.astrbotadapter.platform.spi.TextOutboundMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

final class FabricChatService implements PlatformChatService {
    private final MinecraftServer server;
    private final FabricPlatformScheduler scheduler;

    FabricChatService(MinecraftServer server, FabricPlatformScheduler scheduler) {
        this.server = server;
        this.scheduler = scheduler;
    }

    @Override
    public CompletionStage<Void> broadcast(TextOutboundMessage message) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        scheduler.global(() -> {
            Text text = Text.literal(message.plainText());
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                player.sendMessage(text);
            }
            future.complete(null);
        });
        return future;
    }

    @Override
    public CompletionStage<Void> sendPrivate(PlayerSelector selector, TextOutboundMessage message) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        scheduler.global(() -> {
            ServerPlayerEntity player = findPlayer(selector);
            if (player == null) {
                future.completeExceptionally(new IllegalArgumentException("player not online"));
                return;
            }
            player.sendMessage(Text.literal(message.plainText()));
            future.complete(null);
        });
        return future;
    }

    private ServerPlayerEntity findPlayer(PlayerSelector selector) {
        UUID uuid = selector.uuid();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (uuid != null && player.getUuid().equals(uuid)) {
                return player;
            }
            if (uuid == null && selector.name() != null && player.getGameProfile().getName().equalsIgnoreCase(selector.name())) {
                return player;
            }
        }
        return null;
    }
}
