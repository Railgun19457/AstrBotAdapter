package io.github.railgun19457.astrbotadapter.platform.bukkit;

import io.github.railgun19457.astrbotadapter.platform.spi.PlatformChatService;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformScheduler;
import io.github.railgun19457.astrbotadapter.platform.spi.PlayerSelector;
import io.github.railgun19457.astrbotadapter.platform.spi.TextOutboundMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class BukkitChatService implements PlatformChatService {
    private final Plugin plugin;
    private final PlatformScheduler scheduler;

    public BukkitChatService(Plugin plugin, PlatformScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    @Override
    public CompletionStage<Void> broadcast(TextOutboundMessage message) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        scheduler.global(() -> {
            plugin.getServer().broadcastMessage(message.plainText());
            future.complete(null);
        });
        return future;
    }

    @Override
    public CompletionStage<Void> sendPrivate(PlayerSelector selector, TextOutboundMessage message) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        scheduler.global(() -> {
            Player player = findPlayer(selector);
            if (player == null) {
                future.completeExceptionally(new IllegalArgumentException("player not online"));
                return;
            }
            player.sendMessage(message.plainText());
            future.complete(null);
        });
        return future;
    }

    private Player findPlayer(PlayerSelector selector) {
        UUID uuid = selector.uuid();
        if (uuid != null) {
            return plugin.getServer().getPlayer(uuid);
        }
        return selector.name() == null ? null : plugin.getServer().getPlayerExact(selector.name());
    }
}
