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
        dispatcher.registerRoute("/api/server/info", this::getServerInfo);
        dispatcher.registerRoute("/api/server/status", this::getServerStatus);
        dispatcher.registerRoute("/api/server/tps", this::getServerTps);
    }

    /**
     * 获取服务器信息
     */
    private Response getServerInfo(FullHttpRequest request) {
        if (request.method() != HttpMethod.GET) {
            return Response.error(ErrorCode.REQUEST_PARAM_ERROR, "仅支持GET请求");
        }

        CommonServer server = platformAdapter.getServer();
        
        JsonObject data = new JsonObject();
        data.addProperty("name", server.getName());
        data.addProperty("platform", platformAdapter.getPlatformType().getDisplayName());
        data.addProperty("version", server.getVersion());
        data.addProperty("motd", server.getMotd());
        data.addProperty("maxPlayers", server.getMaxPlayers());
        data.addProperty("onlinePlayers", server.getOnlinePlayerCount());
        data.addProperty("port", server.getPort());
        
        return Response.success(data);
    }

    /**
     * 获取服务器状态
     */
    private Response getServerStatus(FullHttpRequest request) {
        if (request.method() != HttpMethod.GET) {
            return Response.error(ErrorCode.REQUEST_PARAM_ERROR, "仅支持GET请求");
        }

        CommonServer server = platformAdapter.getServer();
        
        JsonObject data = new JsonObject();
        data.addProperty("online", true);
        data.addProperty("onlinePlayers", server.getOnlinePlayerCount());
        data.addProperty("maxPlayers", server.getMaxPlayers());
        data.addProperty("uptime", server.getUptime());
        data.addProperty("uptimeFormatted", formatUptime(server.getUptime()));
        
        // TPS（仅后端服务器有效）
        double[] tps = server.getTps();
        if (tps != null && tps.length >= 3) {
            JsonObject tpsData = new JsonObject();
            tpsData.addProperty("1m", Math.round(tps[0] * 100.0) / 100.0);
            tpsData.addProperty("5m", Math.round(tps[1] * 100.0) / 100.0);
            tpsData.addProperty("15m", Math.round(tps[2] * 100.0) / 100.0);
            data.add("tps", tpsData);
        }

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

        CommonServer server = platformAdapter.getServer();
        double[] tps = server.getTps();
        
        if (tps == null) {
            return Response.error(ErrorCode.FEATURE_DISABLED, "当前平台不支持TPS查询");
        }

        JsonObject data = new JsonObject();
        data.addProperty("1m", Math.round(tps[0] * 100.0) / 100.0);
        data.addProperty("5m", Math.round(tps[1] * 100.0) / 100.0);
        data.addProperty("15m", Math.round(tps[2] * 100.0) / 100.0);

        return Response.success(data);
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
