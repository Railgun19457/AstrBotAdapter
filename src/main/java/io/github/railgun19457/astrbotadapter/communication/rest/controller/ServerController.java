package io.github.railgun19457.astrbotadapter.communication.rest.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.railgun19457.astrbotadapter.communication.protocol.ErrorCode;
import io.github.railgun19457.astrbotadapter.communication.protocol.Response;
import io.github.railgun19457.astrbotadapter.communication.proxy.BackendServerInfo;
import io.github.railgun19457.astrbotadapter.communication.proxy.ProxyBridgeProvider;
import io.github.railgun19457.astrbotadapter.communication.rest.HttpRequestDispatcher;
import io.github.railgun19457.astrbotadapter.platform.PlatformAdapter;
import io.github.railgun19457.astrbotadapter.platform.common.CommonServer;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 服务器信息控制器
 * On Velocity with proxy bridge, aggregates info from all backend servers.
 */
public class ServerController {

    private final PlatformAdapter platformAdapter;
    private final ProxyBridgeProvider proxyBridge; // nullable, only set on Velocity

    public ServerController(PlatformAdapter platformAdapter, ProxyBridgeProvider proxyBridge) {
        this.platformAdapter = platformAdapter;
        this.proxyBridge = proxyBridge;
    }

    /**
     * 注册路由
     */
    public void registerRoutes(HttpRequestDispatcher dispatcher) {
        dispatcher.registerRoute("/api/v1/server/info", this::getServerInfo);
        dispatcher.registerRoute("/api/v1/server/status", this::getServerStatus);
        dispatcher.registerRoute("/api/v1/server/tps", this::getServerTps);
    }

    /**
     * 获取服务器信息
     * Velocity: includes proxy info + all backend servers
     * Backend: only local server info
     */
    private Response getServerInfo(FullHttpRequest request) {
        if (request.method() != HttpMethod.GET) {
            return Response.error(ErrorCode.REQUEST_PARAM_ERROR, "仅支持GET请求");
        }

        JsonObject data = runOnServerThread(() -> {
            CommonServer server = platformAdapter.getServer();

            JsonObject result = new JsonObject();
            result.addProperty("name", server.getName());
            result.addProperty("platform", platformAdapter.getPlatformType().getDisplayName());
            result.addProperty("version", server.getVersion());
            result.addProperty("motd", server.getMotd());
            result.addProperty("maxPlayers", server.getMaxPlayers());
            result.addProperty("onlinePlayers", server.getOnlinePlayerCount());
            result.addProperty("port", server.getPort());
            return result;
        });

        // Proxy mode: append backend server list
        if (proxyBridge != null) {
            JsonArray backends = new JsonArray();
            int totalBackendPlayers = 0;
            int totalBackendMaxPlayers = 0;

            for (Map.Entry<String, BackendServerInfo> entry : proxyBridge.getBackendServers().entrySet()) {
                BackendServerInfo info = entry.getValue();
                if (!info.isAuthenticated()) continue;

                backends.add(info.toJson());
                totalBackendPlayers += info.getOnlineCount();
                totalBackendMaxPlayers += info.getMaxPlayers();
            }

            data.add("backends", backends);
            data.addProperty("backendCount", backends.size());

            // Aggregated totals across all backends
            JsonObject aggregate = new JsonObject();
            aggregate.addProperty("totalOnlinePlayers", totalBackendPlayers);
            aggregate.addProperty("totalMaxPlayers", totalBackendMaxPlayers);
            data.add("aggregate", aggregate);
        }

        return Response.success(data);
    }

    /**
     * 获取服务器状态
     * Velocity: includes proxy status + all backend statuses
     * Backend: only local status
     */
    private Response getServerStatus(FullHttpRequest request) {
        if (request.method() != HttpMethod.GET) {
            return Response.error(ErrorCode.REQUEST_PARAM_ERROR, "仅支持GET请求");
        }

        JsonObject data = runOnServerThread(() -> {
            CommonServer server = platformAdapter.getServer();

            JsonObject result = new JsonObject();
            result.addProperty("online", true);
            result.addProperty("onlinePlayers", server.getOnlinePlayerCount());
            result.addProperty("maxPlayers", server.getMaxPlayers());

            long uptime = server.getUptime();
            result.addProperty("uptime", uptime);
            result.addProperty("uptimeFormatted", formatUptime(uptime));

            // TPS（仅后端服务器有效）
            double[] tps = server.getTps();
            if (tps != null && tps.length >= 3) {
                JsonObject tpsData = new JsonObject();
                tpsData.addProperty("1m", Math.round(tps[0] * 100.0) / 100.0);
                tpsData.addProperty("5m", Math.round(tps[1] * 100.0) / 100.0);
                tpsData.addProperty("15m", Math.round(tps[2] * 100.0) / 100.0);
                result.add("tps", tpsData);
            }

            return result;
        });

        // 内存使用
        Runtime runtime = Runtime.getRuntime();
        JsonObject memory = new JsonObject();
        memory.addProperty("used", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024);
        memory.addProperty("total", runtime.totalMemory() / 1024 / 1024);
        memory.addProperty("max", runtime.maxMemory() / 1024 / 1024);
        data.add("memory", memory);

        // Proxy mode: append backend statuses
        if (proxyBridge != null) {
            JsonArray backends = new JsonArray();

            for (Map.Entry<String, BackendServerInfo> entry : proxyBridge.getBackendServers().entrySet()) {
                BackendServerInfo info = entry.getValue();
                if (!info.isAuthenticated()) continue;

                JsonObject backendStatus = new JsonObject();
                backendStatus.addProperty("name", info.getServerName());
                backendStatus.addProperty("platform", info.getPlatform());
                backendStatus.addProperty("version", info.getVersion());
                backendStatus.addProperty("online", true);
                backendStatus.addProperty("onlinePlayers", info.getOnlineCount());
                backendStatus.addProperty("maxPlayers", info.getMaxPlayers());
                backendStatus.addProperty("uptime", info.getUptime());
                backendStatus.addProperty("uptimeFormatted", formatUptime(info.getUptime()));

                JsonObject backendTps = info.getTps();
                if (backendTps != null) {
                    backendStatus.add("tps", backendTps);
                }

                JsonObject backendMemory = info.getMemory();
                if (backendMemory != null) {
                    backendStatus.add("memory", backendMemory);
                }

                backends.add(backendStatus);
            }

            data.add("backends", backends);
        }

        return Response.success(data);
    }

    /**
     * 获取TPS
     * Velocity: proxy itself has no TPS, returns backend TPS list
     * Backend: returns local TPS
     */
    private Response getServerTps(FullHttpRequest request) {
        if (request.method() != HttpMethod.GET) {
            return Response.error(ErrorCode.REQUEST_PARAM_ERROR, "仅支持GET请求");
        }

        JsonObject data = runOnServerThread(() -> {
            CommonServer server = platformAdapter.getServer();
            double[] tps = server.getTps();

            if (tps == null) {
                return null;
            }

            JsonObject result = new JsonObject();
            result.addProperty("1m", Math.round(tps[0] * 100.0) / 100.0);
            result.addProperty("5m", Math.round(tps[1] * 100.0) / 100.0);
            result.addProperty("15m", Math.round(tps[2] * 100.0) / 100.0);
            return result;
        });

        // Proxy mode: always include backend TPS
        if (proxyBridge != null) {
            JsonObject result = (data != null) ? data : new JsonObject();
            JsonArray backends = new JsonArray();

            for (Map.Entry<String, BackendServerInfo> entry : proxyBridge.getBackendServers().entrySet()) {
                BackendServerInfo info = entry.getValue();
                if (!info.isAuthenticated()) continue;

                JsonObject backendTps = info.getTps();
                if (backendTps != null) {
                    JsonObject backendEntry = new JsonObject();
                    backendEntry.addProperty("name", info.getServerName());
                    backendEntry.add("tps", backendTps);
                    backends.add(backendEntry);
                }
            }

            result.add("backends", backends);
            return Response.success(result);
        }

        if (data == null) {
            return Response.error(ErrorCode.FEATURE_DISABLED, "当前平台不支持TPS查询");
        }

        return Response.success(data);
    }

    private <T> T runOnServerThread(Supplier<T> supplier) {
        if (platformAdapter.getScheduler().isMainThread()) {
            return supplier.get();
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        platformAdapter.getScheduler().runSync(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });

        try {
            return future.get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("获取服务器状态失败", e);
        }
    }

    private String formatUptime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%d天 %d小时 %d分钟", days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return String.format("%d小时 %d分钟", hours, minutes % 60);
        } else {
            return String.format("%d分钟", minutes);
        }
    }
}
