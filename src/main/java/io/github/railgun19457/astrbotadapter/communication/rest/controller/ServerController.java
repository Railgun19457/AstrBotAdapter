package io.github.railgun19457.astrbotadapter.communication.rest.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
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
        dispatcher.registerRoute("/api/v1/server/mspt", this::getServerMspt);
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

        JsonObject data = runOnServerThread(this::buildServerInfoData);
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

        JsonObject data = runOnServerThread(this::buildServerStatusData);
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

        JsonObject data = runOnServerThread(this::buildServerTpsData);
        return Response.success(data);
    }

    /**
     * 获取MSPT
     */
    private Response getServerMspt(FullHttpRequest request) {
        if (request.method() != HttpMethod.GET) {
            return Response.error(ErrorCode.REQUEST_PARAM_ERROR, "仅支持GET请求");
        }

        JsonObject data = runOnServerThread(this::buildServerMsptData);
        return Response.success(data);
    }

    private JsonObject buildServerInfoData() {
        CommonServer server = platformAdapter.getServer();

        JsonArray servers = new JsonArray();
        JsonObject local = new JsonObject();
        local.addProperty("name", server.getName());
        local.addProperty("platform", platformAdapter.getPlatformType().getDisplayName());
        local.addProperty("version", server.getVersion());
        local.addProperty("motd", server.getMotd());
        local.addProperty("onlinePlayers", server.getOnlinePlayerCount());
        local.addProperty("maxPlayers", server.getMaxPlayers());
        local.addProperty("port", server.getPort());
        local.addProperty("scope", platformAdapter.getPlatformType().isProxy() ? "proxy" : "local");
        servers.add(local);

        int totalOnline = server.getOnlinePlayerCount();
        int totalMax = server.getMaxPlayers();
        int backendCount = 0;

        if (proxyBridge != null) {
            for (Map.Entry<String, BackendServerInfo> entry : proxyBridge.getBackendServers().entrySet()) {
                BackendServerInfo info = entry.getValue();
                if (info == null || !info.isAuthenticated()) {
                    continue;
                }

                JsonObject backend = new JsonObject();
                backend.addProperty("name", info.getServerName());
                backend.addProperty("platform", info.getPlatform());
                backend.addProperty("version", info.getVersion());
                backend.addProperty("motd", info.getMotd());
                backend.addProperty("onlinePlayers", info.getOnlineCount());
                backend.addProperty("maxPlayers", info.getMaxPlayers());
                backend.add("port", JsonNull.INSTANCE);
                backend.addProperty("scope", "backend");
                servers.add(backend);

                totalOnline += info.getOnlineCount();
                totalMax += info.getMaxPlayers();
                backendCount++;
            }
        }

        JsonObject aggregate = new JsonObject();
        aggregate.addProperty("totalOnlinePlayers", totalOnline);
        aggregate.addProperty("totalMaxPlayers", totalMax);
        aggregate.addProperty("backendCount", backendCount);

        JsonObject data = new JsonObject();
        data.add("servers", servers);
        data.add("aggregate", aggregate);
        return data;
    }

    private JsonObject buildServerStatusData() {
        CommonServer server = platformAdapter.getServer();

        JsonArray servers = new JsonArray();
        JsonObject local = new JsonObject();
        local.addProperty("name", server.getName());
        local.addProperty("online", true);
        local.addProperty("onlinePlayers", server.getOnlinePlayerCount());
        local.addProperty("maxPlayers", server.getMaxPlayers());
        local.addProperty("uptime", server.getUptime());
        local.addProperty("uptimeFormatted", formatUptime(server.getUptime()));
        local.add("tps", normalizeLocalTps(server.getTps()));
        local.add("mspt", normalizeMspt(server.getMspt()));
        local.add("memory", buildLocalMemory());
        local.addProperty("scope", platformAdapter.getPlatformType().isProxy() ? "proxy" : "local");
        servers.add(local);

        int totalOnline = server.getOnlinePlayerCount();
        int totalMax = server.getMaxPlayers();
        int backendCount = 0;

        if (proxyBridge != null) {
            for (Map.Entry<String, BackendServerInfo> entry : proxyBridge.getBackendServers().entrySet()) {
                BackendServerInfo info = entry.getValue();
                if (info == null || !info.isAuthenticated()) {
                    continue;
                }

                JsonObject backend = new JsonObject();
                backend.addProperty("name", info.getServerName());
                backend.addProperty("online", true);
                backend.addProperty("onlinePlayers", info.getOnlineCount());
                backend.addProperty("maxPlayers", info.getMaxPlayers());
                backend.addProperty("uptime", info.getUptime());
                backend.addProperty("uptimeFormatted", formatUptime(info.getUptime()));
                backend.add("tps", normalizeBackendTps(info.getTps()));
                backend.add("mspt", normalizeMspt(info.getMspt()));
                backend.add("memory", normalizeMemory(info.getMemory()));
                backend.addProperty("scope", "backend");
                servers.add(backend);

                totalOnline += info.getOnlineCount();
                totalMax += info.getMaxPlayers();
                backendCount++;
            }
        }

        JsonObject aggregate = new JsonObject();
        aggregate.addProperty("totalOnlinePlayers", totalOnline);
        aggregate.addProperty("totalMaxPlayers", totalMax);
        aggregate.addProperty("backendCount", backendCount);

        JsonObject data = new JsonObject();
        data.add("servers", servers);
        data.add("aggregate", aggregate);
        return data;
    }

    private JsonObject buildServerTpsData() {
        CommonServer server = platformAdapter.getServer();
        JsonArray servers = new JsonArray();

        JsonObject local = new JsonObject();
        local.addProperty("name", server.getName());
        local.add("tps", normalizeLocalTps(server.getTps()));
        local.addProperty("scope", platformAdapter.getPlatformType().isProxy() ? "proxy" : "local");
        servers.add(local);

        if (proxyBridge != null) {
            for (Map.Entry<String, BackendServerInfo> entry : proxyBridge.getBackendServers().entrySet()) {
                BackendServerInfo info = entry.getValue();
                if (info == null || !info.isAuthenticated()) {
                    continue;
                }

                JsonObject backend = new JsonObject();
                backend.addProperty("name", info.getServerName());
                backend.add("tps", normalizeBackendTps(info.getTps()));
                backend.addProperty("scope", "backend");
                servers.add(backend);
            }
        }

        JsonObject data = new JsonObject();
        data.add("servers", servers);
        return data;
    }

    private JsonObject buildServerMsptData() {
        CommonServer server = platformAdapter.getServer();
        JsonArray servers = new JsonArray();

        JsonObject local = new JsonObject();
        local.addProperty("name", server.getName());
        local.add("mspt", normalizeMspt(server.getMspt()));
        local.addProperty("scope", platformAdapter.getPlatformType().isProxy() ? "proxy" : "local");
        servers.add(local);

        if (proxyBridge != null) {
            for (Map.Entry<String, BackendServerInfo> entry : proxyBridge.getBackendServers().entrySet()) {
                BackendServerInfo info = entry.getValue();
                if (info == null || !info.isAuthenticated()) {
                    continue;
                }

                JsonObject backend = new JsonObject();
                backend.addProperty("name", info.getServerName());
                backend.add("mspt", normalizeMspt(info.getMspt()));
                backend.addProperty("scope", "backend");
                servers.add(backend);
            }
        }

        JsonObject data = new JsonObject();
        data.add("servers", servers);
        return data;
    }

    private JsonObject buildLocalMemory() {
        Runtime runtime = Runtime.getRuntime();
        JsonObject memory = new JsonObject();
        memory.addProperty("used", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024);
        memory.addProperty("total", runtime.totalMemory() / 1024 / 1024);
        memory.addProperty("max", runtime.maxMemory() / 1024 / 1024);
        return memory;
    }

    private JsonElement normalizeMemory(JsonObject rawMemory) {
        if (rawMemory == null) {
            return JsonNull.INSTANCE;
        }

        JsonObject normalized = new JsonObject();
        if (rawMemory.has("used")) normalized.add("used", rawMemory.get("used"));
        if (rawMemory.has("total")) {
            normalized.add("total", rawMemory.get("total"));
        } else if (rawMemory.has("used") && rawMemory.has("free")) {
            try {
                double total = rawMemory.get("used").getAsDouble() + rawMemory.get("free").getAsDouble();
                normalized.addProperty("total", round2(total));
            } catch (Exception e) {
                normalized.add("total", JsonNull.INSTANCE);
            }
        } else {
            normalized.add("total", JsonNull.INSTANCE);
        }
        if (rawMemory.has("max")) normalized.add("max", rawMemory.get("max"));
        return normalized;
    }

    private JsonElement normalizeLocalTps(double[] tps) {
        if (tps == null || tps.length < 3) {
            return JsonNull.INSTANCE;
        }
        JsonObject json = new JsonObject();
        json.addProperty("1m", round2(tps[0]));
        json.addProperty("5m", round2(tps[1]));
        json.addProperty("15m", round2(tps[2]));
        return json;
    }

    private JsonElement normalizeBackendTps(JsonObject tps) {
        if (tps == null) {
            return JsonNull.INSTANCE;
        }
        JsonObject json = new JsonObject();
        addNumberWithFallback(tps, json, "1m", "tps1m");
        addNumberWithFallback(tps, json, "5m", "tps5m");
        addNumberWithFallback(tps, json, "15m", "tps15m");
        if (!json.has("1m") && !json.has("5m") && !json.has("15m")) {
            return JsonNull.INSTANCE;
        }
        return json;
    }

    private JsonElement normalizeMspt(Double mspt) {
        if (mspt == null) {
            return JsonNull.INSTANCE;
        }
        return new com.google.gson.JsonPrimitive(round2(mspt));
    }

    private void addNumberWithFallback(JsonObject source, JsonObject target,
                                       String preferredKey, String fallbackKey) {
        if (source.has(preferredKey) && !source.get(preferredKey).isJsonNull()) {
            target.add(preferredKey, source.get(preferredKey));
            return;
        }
        if (source.has(fallbackKey) && !source.get(fallbackKey).isJsonNull()) {
            try {
                target.addProperty(preferredKey, round2(source.get(fallbackKey).getAsDouble()));
            } catch (Exception ignored) {
            }
        }
    }

    private double round2(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private String formatUptime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%d天 %d小时 %d分钟", days, hours % 24, minutes % 60);
        }
        if (hours > 0) {
            return String.format("%d小时 %d分钟", hours, minutes % 60);
        }
        return String.format("%d分钟", minutes);
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

}
