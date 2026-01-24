package io.github.railgun19457.astrbotadapter.platform.bukkit;

import io.github.railgun19457.astrbotadapter.platform.common.CommonServer;
import org.bukkit.Server;

/**
 * Bukkit服务器包装类
 */
public class BukkitServer implements CommonServer {

    private final Server server;
    private final long startTime;

    public BukkitServer(Server server) {
        this.server = server;
        this.startTime = System.currentTimeMillis();
    }

    @Override
    public String getName() {
        return server.getName();
    }

    @Override
    public String getMotd() {
        return server.getMotd();
    }

    @Override
    public String getVersion() {
        return server.getVersion();
    }

    @Override
    public int getMaxPlayers() {
        return server.getMaxPlayers();
    }

    @Override
    public int getOnlinePlayerCount() {
        return server.getOnlinePlayers().size();
    }

    @Override
    public double[] getTps() {
        try {
            // 尝试获取Paper的TPS
            return server.getTPS();
        } catch (NoSuchMethodError e) {
            // 非Paper服务端，返回估算值
            return new double[]{20.0, 20.0, 20.0};
        }
    }

    @Override
    public long getUptime() {
        return System.currentTimeMillis() - startTime;
    }

    @Override
    public int getPort() {
        return server.getPort();
    }

    @Override
    public String getIp() {
        String ip = server.getIp();
        return ip.isEmpty() ? "0.0.0.0" : ip;
    }
}
