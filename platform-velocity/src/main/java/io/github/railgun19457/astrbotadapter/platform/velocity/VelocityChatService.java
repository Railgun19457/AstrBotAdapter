package io.github.railgun19457.astrbotadapter.platform.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformChatService;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformScheduler;
import io.github.railgun19457.astrbotadapter.platform.spi.PlayerSelector;
import io.github.railgun19457.astrbotadapter.platform.spi.TextOutboundMessage;
import net.kyori.adventure.text.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

final class VelocityChatService implements PlatformChatService {
    private final ProxyServer proxy;
    private final PlatformScheduler scheduler;

    VelocityChatService(ProxyServer proxy, PlatformScheduler scheduler) {
        this.proxy = proxy;
        this.scheduler = scheduler;
    }

    @Override
    public CompletionStage<Void> broadcast(TextOutboundMessage message) {
        return scheduler.callGlobal(() -> {
            proxy.sendMessage(Component.text(message.plainText()));
            return null;
        });
    }

    @Override
    public CompletionStage<Void> sendPrivate(PlayerSelector player, TextOutboundMessage message) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        scheduler.global(() -> {
            Player target = find(player);
            if (target == null) {
                future.completeExceptionally(new IllegalArgumentException("player not online"));
                return;
            }
            target.sendMessage(Component.text(message.plainText()));
            future.complete(null);
        });
        return future;
    }

    private Player find(PlayerSelector selector) {
        UUID uuid = selector.uuid();
        if (uuid != null) {
            return proxy.getPlayer(uuid).orElse(null);
        }
        return selector.name() == null ? null : proxy.getPlayer(selector.name()).orElse(null);
    }
}
