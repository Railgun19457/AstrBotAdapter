package io.github.railgun19457.astrbotadapter.platform.bukkit;

import io.github.railgun19457.astrbotadapter.platform.spi.PlatformPlayerService;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformScheduler;
import io.github.railgun19457.astrbotadapter.platform.spi.PlayerDetail;
import io.github.railgun19457.astrbotadapter.platform.spi.PlayerPosition;
import io.github.railgun19457.astrbotadapter.platform.spi.PlayerQuery;
import io.github.railgun19457.astrbotadapter.platform.spi.PlayerSummary;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public final class BukkitPlayerService implements PlatformPlayerService {
    private final Plugin plugin;
    private final PlatformScheduler scheduler;
    private final Supplier<String> serverIdSupplier;

    public BukkitPlayerService(Plugin plugin, PlatformScheduler scheduler) {
        this(plugin, scheduler, () -> "standalone");
    }

    public BukkitPlayerService(Plugin plugin, PlatformScheduler scheduler, Supplier<String> serverIdSupplier) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.serverIdSupplier = serverIdSupplier == null ? () -> "standalone" : serverIdSupplier;
    }

    @Override
    public CompletionStage<List<PlayerSummary>> onlinePlayers(boolean includeDetails) {
        return scheduler.callGlobal(() -> plugin.getServer().getOnlinePlayers().stream().map(this::summary).toList());
    }

    @Override
    public CompletionStage<Optional<PlayerDetail>> findPlayer(PlayerQuery query, boolean includeHistory) {
        return scheduler.callGlobal(() -> {
            Player online = findOnline(query);
            if (online != null) {
                return Optional.of(detail(online));
            }
            if (!includeHistory) {
                return Optional.empty();
            }
            return findOffline(query).map(this::offlineDetail);
        });
    }

    private Player findOnline(PlayerQuery query) {
        if (query.type() == PlayerQuery.Type.UUID) {
            return plugin.getServer().getPlayer(UUID.fromString(query.value()));
        }
        return plugin.getServer().getPlayerExact(query.value());
    }

    private Optional<OfflinePlayer> findOffline(PlayerQuery query) {
        if (query.type() == PlayerQuery.Type.UUID) {
            try {
                OfflinePlayer offline = plugin.getServer().getOfflinePlayer(UUID.fromString(query.value()));
                return offline.hasPlayedBefore() || offline.isOnline() ? Optional.of(offline) : Optional.empty();
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        }
        OfflinePlayer offline = plugin.getServer().getOfflinePlayer(query.value());
        return offline.hasPlayedBefore() || offline.isOnline() ? Optional.of(offline) : Optional.empty();
    }

    private PlayerSummary summary(Player player) {
        return new PlayerSummary(player.getUniqueId(), player.getName(), player.getDisplayName(), serverIdSupplier.get(), player.getWorld().getName(), player.getGameMode().name().toLowerCase(), player.getPing());
    }

    private PlayerDetail detail(Player player) {
        Location location = player.getLocation();
        Double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH) == null ? null : player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        long firstPlayed = player.getFirstPlayed();
        long lastPlayed = player.getLastPlayed();
        Long onlineDurationMs = firstPlayed > 0L ? Math.max(0L, System.currentTimeMillis() - firstPlayed) : null;
        return new PlayerDetail(
            player.getUniqueId(),
            player.getName(),
            player.getDisplayName(),
            true,
            serverIdSupplier.get(),
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
            firstPlayed > 0L ? firstPlayed : null,
            lastPlayed > 0L ? lastPlayed : null,
            onlineDurationMs
        );
    }

    private PlayerDetail offlineDetail(OfflinePlayer offline) {
        long firstPlayed = offline.getFirstPlayed();
        long lastPlayed = offline.getLastPlayed();
        String name = offline.getName() == null ? offline.getUniqueId().toString() : offline.getName();
        return new PlayerDetail(
            offline.getUniqueId(),
            name,
            name,
            false,
            serverIdSupplier.get(),
            null,
            null,
            0,
            null,
            null,
            null,
            null,
            null,
            offline.isOp(),
            null,
            null,
            firstPlayed > 0L ? firstPlayed : null,
            lastPlayed > 0L ? lastPlayed : null,
            null
        );
    }
}
