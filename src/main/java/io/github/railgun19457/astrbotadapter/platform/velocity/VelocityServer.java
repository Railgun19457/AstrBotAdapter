package io.github.railgun19457.astrbotadapter.platform.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import io.github.railgun19457.astrbotadapter.platform.common.CommonServer;

/**
 * Velocity服务器包装类
 */
public class VelocityServer implements CommonServer {

    private final ProxyServer proxy;
    private final long startTime;

    public VelocityServer(ProxyServer proxy) {
        this.proxy = proxy;
        this.startTime = System.currentTimeMillis();
    }

    @Override
    public String getName() {
        return "Velocity";
    }

    @Override
    public String getMotd() {
        return proxy.getConfiguration().getMotd().toString();
    }

    @Override
    public String getVersion() {
        return proxy.getVersion().getVersion();
    }

    @Override
    public int getMaxPlayers() {
        return proxy.getConfiguration().getShowMaxPlayers();
    }

    @Override
    public int getOnlinePlayerCount() {
        return proxy.getPlayerCount();
    }

    @Override
    public double[] getTps() {
        // Velocity代理没有TPS概念
        return new double[]{20.0, 20.0, 20.0};
    }

    @Override
    public long getUptime() {
        return System.currentTimeMillis() - startTime;
    }

    @Override
    public int getPort() {
        return proxy.getBoundAddress().getPort();
    }

    @Override
    public String getIp() {
        String host = proxy.getBoundAddress().getHostString();
        return host != null ? host : "0.0.0.0";
    }
}
