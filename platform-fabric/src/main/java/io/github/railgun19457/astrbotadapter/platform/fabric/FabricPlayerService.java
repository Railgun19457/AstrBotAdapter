package io.github.railgun19457.astrbotadapter.platform.fabric;

import io.github.railgun19457.astrbotadapter.platform.spi.PlatformPlayerService;
import io.github.railgun19457.astrbotadapter.platform.spi.PlayerDetail;
import io.github.railgun19457.astrbotadapter.platform.spi.PlayerPosition;
import io.github.railgun19457.astrbotadapter.platform.spi.PlayerQuery;
import io.github.railgun19457.astrbotadapter.platform.spi.PlayerSummary;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

final class FabricPlayerService implements PlatformPlayerService {
    private final MinecraftServer server;
    private final FabricPlatformScheduler scheduler;

    FabricPlayerService(MinecraftServer server, FabricPlatformScheduler scheduler) {
        this.server = server;
        this.scheduler = scheduler;
    }

    @Override
    public CompletionStage<List<PlayerSummary>> onlinePlayers(boolean includeDetails) {
        return scheduler.callGlobal(() -> server.getPlayerManager().getPlayerList().stream().map(this::summary).toList());
    }

    @Override
    public CompletionStage<Optional<PlayerDetail>> findPlayer(PlayerQuery query, boolean includeHistory) {
        return scheduler.callGlobal(() -> Optional.ofNullable(find(query)).map(this::detail));
    }

    private ServerPlayerEntity find(PlayerQuery query) {
        UUID uuid = null;
        if (query.type() == PlayerQuery.Type.UUID) {
            try {
                uuid = UUID.fromString(query.value());
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (uuid != null && player.getUuid().equals(uuid)) {
                return player;
            }
            if (uuid == null && player.getGameProfile().getName().equalsIgnoreCase(query.value())) {
                return player;
            }
        }
        return null;
    }

    private PlayerSummary summary(ServerPlayerEntity player) {
        return new PlayerSummary(
            player.getUuid(),
            player.getGameProfile().getName(),
            displayName(player),
            "standalone",
            worldName(player),
            player.interactionManager.getGameMode().getName(),
            -1
        );
    }

    private PlayerDetail detail(ServerPlayerEntity player) {
        return new PlayerDetail(
            player.getUuid(),
            player.getGameProfile().getName(),
            displayName(player),
            true,
            "standalone",
            worldName(player),
            player.interactionManager.getGameMode().getName(),
            -1,
            (double) player.getHealth(),
            (double) player.getMaxHealth(),
            player.getHungerManager().getFoodLevel(),
            player.experienceLevel,
            (double) player.experienceProgress,
            server.getPlayerManager().isOperator(player.getGameProfile()),
            player.getAbilities().allowFlying,
            new PlayerPosition(worldName(player), player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch()),
            null,
            null,
            null
        );
    }

    private String displayName(ServerPlayerEntity player) {
        Text displayName = player.getDisplayName();
        return displayName == null ? player.getGameProfile().getName() : displayName.getString();
    }

    private String worldName(ServerPlayerEntity player) {
        return player.getWorld().getRegistryKey().getValue().toString();
    }
}
