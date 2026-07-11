package io.github.railgun19457.astrbotadapter.platform.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformPlayerService;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformScheduler;
import io.github.railgun19457.astrbotadapter.platform.spi.PlayerDetail;
import io.github.railgun19457.astrbotadapter.platform.spi.PlayerQuery;
import io.github.railgun19457.astrbotadapter.platform.spi.PlayerSummary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

final class VelocityPlayerService implements PlatformPlayerService {
    private final ProxyServer proxy;
    private final PlatformScheduler scheduler;

    VelocityPlayerService(ProxyServer proxy, PlatformScheduler scheduler) {
        this.proxy = proxy;
        this.scheduler = scheduler;
    }

    @Override
    public CompletionStage<List<PlayerSummary>> onlinePlayers(boolean includeDetails) {
        return scheduler.callGlobal(() -> proxy.getAllPlayers().stream().map(this::summary).toList());
    }

    @Override
    public CompletionStage<Optional<PlayerDetail>> findPlayer(PlayerQuery query, boolean includeHistory) {
        return scheduler.callGlobal(() -> Optional.ofNullable(find(query)).map(this::detail));
    }

    private Player find(PlayerQuery query) {
        if (query.type() == PlayerQuery.Type.UUID) {
            return proxy.getPlayer(UUID.fromString(query.value())).orElse(null);
        }
        return proxy.getPlayer(query.value()).orElse(null);
    }

    private PlayerSummary summary(Player player) {
        String server = player.getCurrentServer().map(connection -> connection.getServerInfo().getName()).orElse("velocity-main");
        return new PlayerSummary(player.getUniqueId(), player.getUsername(), player.getUsername(), server, null, null, (int) player.getPing());
    }

    private PlayerDetail detail(Player player) {
        String server = player.getCurrentServer().map(connection -> connection.getServerInfo().getName()).orElse("velocity-main");
        return new PlayerDetail(player.getUniqueId(), player.getUsername(), player.getUsername(), true, server, null, null, (int) player.getPing());
    }
}
