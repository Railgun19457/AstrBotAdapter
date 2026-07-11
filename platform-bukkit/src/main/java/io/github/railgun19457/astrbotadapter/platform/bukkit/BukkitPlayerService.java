package io.github.railgun19457.astrbotadapter.platform.bukkit;

import io.github.railgun19457.astrbotadapter.platform.spi.PlatformPlayerService;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformScheduler;
import io.github.railgun19457.astrbotadapter.platform.spi.PlayerDetail;
import io.github.railgun19457.astrbotadapter.platform.spi.PlayerPosition;
import io.github.railgun19457.astrbotadapter.platform.spi.PlayerQuery;
import io.github.railgun19457.astrbotadapter.platform.spi.PlayerSummary;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public final class BukkitPlayerService implements PlatformPlayerService {
    private final Plugin plugin;
    private final PlatformScheduler scheduler;

    public BukkitPlayerService(Plugin plugin, PlatformScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    @Override
    public CompletionStage<List<PlayerSummary>> onlinePlayers(boolean includeDetails) {
        return scheduler.callGlobal(() -> plugin.getServer().getOnlinePlayers().stream().map(this::summary).toList());
    }

    @Override
    public CompletionStage<Optional<PlayerDetail>> findPlayer(PlayerQuery query, boolean includeHistory) {
        return scheduler.callGlobal(() -> Optional.ofNullable(find(query)).map(this::detail));
    }

    private Player find(PlayerQuery query) {
        if (query.type() == PlayerQuery.Type.UUID) {
            return plugin.getServer().getPlayer(UUID.fromString(query.value()));
        }
        return plugin.getServer().getPlayerExact(query.value());
    }

    private PlayerSummary summary(Player player) {
        return new PlayerSummary(player.getUniqueId(), player.getName(), player.getDisplayName(), "standalone", player.getWorld().getName(), player.getGameMode().name().toLowerCase(), player.getPing());
    }

    private PlayerDetail detail(Player player) {
        Location location = player.getLocation();
        Double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH) == null ? null : player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        return new PlayerDetail(
            player.getUniqueId(),
            player.getName(),
            player.getDisplayName(),
            true,
            "standalone",
            player.getWorld().getName(),
            player.getGameMode().name().toLowerCase(),
            player.getPing(),
            player.getHealth(),
            maxHealth,
            player.getFoodLevel(),
            player.getLevel(),
            (double) player.getExp(),
            player.isOp(),
            player.getAllowFlight(),
            new PlayerPosition(location.getWorld() == null ? player.getWorld().getName() : location.getWorld().getName(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch()),
            player.getFirstPlayed(),
            player.getLastPlayed(),
            null
        );
    }
}
