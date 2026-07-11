package io.github.railgun19457.astrbotadapter.platform.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformScheduler;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformServerService;
import io.github.railgun19457.astrbotadapter.platform.spi.ServerSnapshot;
import io.github.railgun19457.astrbotadapter.platform.spi.ServerStatusItem;
import io.github.railgun19457.astrbotadapter.protocol.ServerRole;
import io.github.railgun19457.astrbotadapter.protocol.Status;

import java.util.concurrent.CompletionStage;

final class VelocityServerService implements PlatformServerService {
    private final ProxyServer proxy;
    private final PlatformScheduler scheduler;

    VelocityServerService(ProxyServer proxy, PlatformScheduler scheduler) {
        this.proxy = proxy;
        this.scheduler = scheduler;
    }

    @Override
    public CompletionStage<ServerStatusItem> status(boolean includePerformance) {
        return scheduler.callGlobal(() -> new ServerStatusItem(
            "velocity-main",
            "Velocity",
            ServerRole.VELOCITY,
            Status.success(),
            "velocity",
            proxy.getVersion().getVersion(),
            proxy.getPlayerCount(),
            proxy.getConfiguration().getShowMaxPlayers()
        ));
    }

    @Override
    public ServerSnapshot snapshot() {
        return new ServerSnapshot("velocity-main", ServerRole.VELOCITY, proxy.getPlayerCount(), proxy.getConfiguration().getShowMaxPlayers(), System.currentTimeMillis());
    }
}
