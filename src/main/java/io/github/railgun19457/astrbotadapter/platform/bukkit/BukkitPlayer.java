package io.github.railgun19457.astrbotadapter.platform.bukkit;

import io.github.railgun19457.astrbotadapter.platform.common.CommonPlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Bukkit玩家包装类
 */
public class BukkitPlayer implements CommonPlayer {

    private final Player player;

    public BukkitPlayer(Player player) {
        this.player = player;
    }

    @Override
    public UUID getUniqueId() {
        return player.getUniqueId();
    }

    @Override
    public String getName() {
        return player.getName();
    }

    @Override
    public String getDisplayName() {
        return player.getDisplayName();
    }

    @Override
    public int getPing() {
        return player.getPing();
    }

    @Override
    public void sendMessage(String message) {
        player.sendMessage(message);
    }

    @Override
    public boolean hasPermission(String permission) {
        return player.hasPermission(permission);
    }

    @Override
    public boolean isOnline() {
        return player.isOnline();
    }

    @Override
    public double getHealth() {
        return player.getHealth();
    }

    @Override
    public double getMaxHealth() {
        return player.getMaxHealth();
    }

    @Override
    public int getLevel() {
        return player.getLevel();
    }

    @Override
    public String getWorld() {
        return player.getWorld().getName();
    }

    @Override
    public PlayerLocation getLocation() {
        org.bukkit.Location loc = player.getLocation();
        return new PlayerLocation(
                loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch()
        );
    }

    @Override
    public String getGameMode() {
        return player.getGameMode().name();
    }

    @Override
    public int getFoodLevel() {
        return player.getFoodLevel();
    }

    @Override
    public float getExp() {
        return player.getExp();
    }

    @Override
    public int getTotalExp() {
        return player.getTotalExperience();
    }

    @Override
    public boolean isOp() {
        return player.isOp();
    }

    @Override
    public boolean isFlying() {
        return player.isFlying();
    }

    @Override
    public long getFirstPlayed() {
        return player.getFirstPlayed();
    }

    @Override
    public long getLastPlayed() {
        return player.getLastPlayed();
    }

    @Override
    public long getOnlineTime() {
        try {
            int ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
            return ticks * 50L;
        } catch (Exception ignored) {
            return -1;
        }
    }

    /**
     * 获取原始Bukkit玩家对象
     */
    public Player getBukkitPlayer() {
        return player;
    }
}
