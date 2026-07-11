package io.github.railgun19457.astrbotadapter.platform.fabric;

import io.github.railgun19457.astrbotadapter.platform.spi.PlatformServerService;
import io.github.railgun19457.astrbotadapter.platform.spi.RuntimeMode;
import io.github.railgun19457.astrbotadapter.platform.spi.ServerSnapshot;
import io.github.railgun19457.astrbotadapter.platform.spi.ServerStatusItem;
import io.github.railgun19457.astrbotadapter.protocol.ServerRole;
import io.github.railgun19457.astrbotadapter.protocol.Status;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.CompletionStage;

final class FabricServerService implements PlatformServerService {
    private final MinecraftServer server;
    private final FabricPlatformScheduler scheduler;
    private final ServerRole role;

    FabricServerService(MinecraftServer server, FabricPlatformScheduler scheduler, RuntimeMode runtimeMode) {
        this.server = server;
        this.scheduler = scheduler;
        this.role = runtimeMode == RuntimeMode.VELOCITY_BACKEND ? ServerRole.BACKEND : ServerRole.STANDALONE;
    }

    @Override
    public CompletionStage<ServerStatusItem> status(boolean includePerformance) {
        return scheduler.callGlobal(() -> new ServerStatusItem(
            "standalone",
            "Fabric Server",
            role,
            Status.success(),
            "fabric",
            server.getVersion(),
            server.getPlayerManager().getPlayerList().size(),
            server.getPlayerManager().getMaxPlayerCount()
        ));
    }

    @Override
    public ServerSnapshot snapshot() {
        return new ServerSnapshot("standalone", role, server.getPlayerManager().getPlayerList().size(), server.getPlayerManager().getMaxPlayerCount(), System.currentTimeMillis());
    }
}
