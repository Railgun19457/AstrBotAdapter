package io.github.railgun19457.astrbotadapter.platform.velocity;

import com.velocitypowered.api.proxy.Player;
import io.github.railgun19457.astrbotadapter.platform.common.CommonPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.UUID;

/**
 * Velocity玩家包装类
 */
public class VelocityPlayer implements CommonPlayer {

    private final Player player;

    public VelocityPlayer(Player player) {
        this.player = player;
    }

    @Override
    public UUID getUniqueId() {
        return player.getUniqueId();
    }

    @Override
    public String getName() {
        return player.getUsername();
    }

    @Override
    public String getDisplayName() {
        return player.getUsername();
    }

    @Override
    public int getPing() {
        return (int) player.getPing();
    }

    @Override
    public void sendMessage(String message) {
        player.sendMessage(Component.text(message));
    }

    @Override
    public boolean hasPermission(String permission) {
        return player.hasPermission(permission);
    }

    @Override
    public boolean isOnline() {
        return player.isActive();
    }

    @Override
    public double getHealth() {
        // Velocity代理无法获取玩家生命值
        return 20.0;
    }

    @Override
    public double getMaxHealth() {
        return 20.0;
    }

    @Override
    public int getLevel() {
        // Velocity代理无法获取玩家等级
        return 0;
    }

    @Override
    public String getWorld() {
        // 返回当前连接的服务器名称
        return player.getCurrentServer()
                .map(server -> server.getServerInfo().getName())
                .orElse("unknown");
    }

    @Override
    public PlayerLocation getLocation() {
        // Velocity代理无法获取玩家位置
        String serverName = getWorld();
        return new PlayerLocation(serverName, 0, 0, 0, 0, 0);
    }

    @Override
    public String getGameMode() {
        // Velocity代理无法获取游戏模式
        return "UNKNOWN";
    }

    @Override
    public String getConnectedServer() {
        return player.getCurrentServer()
                .map(server -> server.getServerInfo().getName())
                .orElse(null);
    }

    /**
     * 获取原始Velocity玩家对象
     */
    public Player getVelocityPlayer() {
        return player;
    }

    /**
     * 获取玩家当前所在服务器名称
     */
    public String getCurrentServerName() {
        return player.getCurrentServer()
                .map(server -> server.getServerInfo().getName())
                .orElse(null);
    }
}
