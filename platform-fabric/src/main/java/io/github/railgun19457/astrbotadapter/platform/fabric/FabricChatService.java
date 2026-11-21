package io.github.railgun19457.astrbotadapter.platform.fabric;

import io.github.railgun19457.astrbotadapter.platform.spi.PlatformChatService;
import io.github.railgun19457.astrbotadapter.platform.spi.PlayerSelector;
import io.github.railgun19457.astrbotadapter.platform.spi.TextOutboundMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

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
            Component text = Component.literal(message.plainText());
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.sendSystemMessage(text);
            }
            future.complete(null);
        });
        return future;
    }

    @Override
    public CompletionStage<Void> sendPrivate(PlayerSelector selector, TextOutboundMessage message) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        scheduler.global(() -> {
            ServerPlayer player = findPlayer(selector);
            if (player == null) {
                future.completeExceptionally(new IllegalArgumentException("player not online"));
                return;
            }
            player.sendSystemMessage(Component.literal(message.plainText()));
            future.complete(null);
        });
        return future;
    }

    private ServerPlayer findPlayer(PlayerSelector selector) {
        UUID uuid = selector.uuid();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (uuid != null && player.getUUID().equals(uuid)) {
                return player;
            }
            if (uuid == null && selector.name() != null && player.getGameProfile().name().equalsIgnoreCase(selector.name())) {
                return player;
            }
        }
        return null;
    }
}
