package io.github.railgun19457.astrbotadapter.platform.fabric;

import io.github.railgun19457.astrbotadapter.platform.spi.PlatformPlayerService;
import io.github.railgun19457.astrbotadapter.platform.spi.PlayerDetail;
import io.github.railgun19457.astrbotadapter.platform.spi.PlayerPosition;
import io.github.railgun19457.astrbotadapter.platform.spi.PlayerQuery;
import io.github.railgun19457.astrbotadapter.platform.spi.PlayerSummary;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

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
        return scheduler.callGlobal(() -> server.getPlayerList().getPlayers().stream().map(this::summary).toList());
    }

    @Override
    public CompletionStage<Optional<PlayerDetail>> findPlayer(PlayerQuery query, boolean includeHistory) {
        return scheduler.callGlobal(() -> Optional.ofNullable(find(query)).map(this::detail));
    }

    private ServerPlayer find(PlayerQuery query) {
        UUID uuid = null;
        if (query.type() == PlayerQuery.Type.UUID) {
            try {
                uuid = UUID.fromString(query.value());
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (uuid != null && player.getUUID().equals(uuid)) {
                return player;
            }
            if (uuid == null && player.getGameProfile().name().equalsIgnoreCase(query.value())) {
                return player;
            }
        }
        return null;
    }

    private PlayerSummary summary(ServerPlayer player) {
        return new PlayerSummary(
            player.getUUID(),
            player.getGameProfile().name(),
            displayName(player),
            "standalone",
            worldName(player),
            player.gameMode.getGameModeForPlayer().getName(),
            -1
        );
    }

    private PlayerDetail detail(ServerPlayer player) {
        return new PlayerDetail(
            player.getUUID(),
            player.getGameProfile().name(),
            displayName(player),
            true,
            "standalone",
            worldName(player),
            player.gameMode.getGameModeForPlayer().getName(),
            -1,
            (double) player.getHealth(),
            (double) player.getMaxHealth(),
            player.getFoodData().getFoodLevel(),
            player.experienceLevel,
            (double) player.experienceProgress,
            isOperator(player),
            player.getAbilities().mayfly,
            new PlayerPosition(worldName(player), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()),
            null,
            null,
            null
        );
    }

    private boolean isOperator(ServerPlayer player) {
        PlayerList playerList = server.getPlayerList();
        return playerList.isOp(player.nameAndId());
    }

    private String displayName(ServerPlayer player) {
        Component displayName = player.getDisplayName();
        return displayName == null ? player.getGameProfile().name() : displayName.getString();
    }

    private String worldName(ServerPlayer player) {
        return player.level().dimension().identifier().toString();
    }
}
