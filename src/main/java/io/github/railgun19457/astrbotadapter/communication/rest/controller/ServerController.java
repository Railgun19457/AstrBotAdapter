package io.github.railgun19457.astrbotadapter.communication.rest.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.railgun19457.astrbotadapter.communication.protocol.ErrorCode;
import io.github.railgun19457.astrbotadapter.communication.protocol.Response;
import io.github.railgun19457.astrbotadapter.communication.rest.HttpRequestDispatcher;
import io.github.railgun19457.astrbotadapter.platform.PlatformAdapter;
import io.github.railgun19457.astrbotadapter.platform.common.CommonServer;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 服务器信息控制器
 */
public class ServerController {

    private final PlatformAdapter platformAdapter;

    public ServerController(PlatformAdapter platformAdapter) {
        this.platformAdapter = platformAdapter;
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

        return Response.success(data);
    }

    /**
     * 获取服务器状态
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

        return Response.success(data);
    }

    /**
     * 获取TPS
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
