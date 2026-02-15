package io.github.railgun19457.astrbotadapter.communication.rest.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.railgun19457.astrbotadapter.communication.protocol.ErrorCode;
import io.github.railgun19457.astrbotadapter.communication.protocol.Response;
import io.github.railgun19457.astrbotadapter.communication.proxy.BackendServerInfo;
import io.github.railgun19457.astrbotadapter.communication.proxy.ProxyBridgeProvider;
import io.github.railgun19457.astrbotadapter.communication.rest.HttpRequestDispatcher;
import io.github.railgun19457.astrbotadapter.platform.PlatformAdapter;
import io.github.railgun19457.astrbotadapter.platform.common.CommonPlayer;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 玩家信息控制器
 * On Velocity with proxy bridge, enriches player data from backend cache.
 */
public class PlayerController {

    private final PlatformAdapter platformAdapter;
    private final ProxyBridgeProvider proxyBridge; // nullable

    public PlayerController(PlatformAdapter platformAdapter, ProxyBridgeProvider proxyBridge) {
        this.platformAdapter = platformAdapter;
        this.proxyBridge = proxyBridge;
    }

    /**
     * 注册路由
     */
    public void registerRoutes(HttpRequestDispatcher dispatcher) {
        dispatcher.registerRoute("/api/v1/players", this::getPlayerList);
        dispatcher.registerRoute("/api/v1/players/*", this::getPlayerInfo);
    }

    /**
     * 获取玩家列表
     */
    private Response getPlayerList(FullHttpRequest request) {
        if (request.method() != HttpMethod.GET) {
            return Response.error(ErrorCode.REQUEST_PARAM_ERROR, "仅支持GET请求");
        }

        JsonArray players = new JsonArray();
        
        for (CommonPlayer player : platformAdapter.getOnlinePlayers()) {
            JsonObject playerData = new JsonObject();
            playerData.addProperty("uuid", player.getUniqueId().toString());
            playerData.addProperty("name", player.getName());
            playerData.addProperty("displayName", player.getDisplayName());
            playerData.addProperty("ping", player.getPing());

            if (platformAdapter.getPlatformType().isBackend()) {
                // Backend: use live data
                String world = player.getWorld();
                String gameMode = player.getGameMode();
                if (world != null) {
                    playerData.addProperty("world", world);
                }
                if (gameMode != null) {
                    playerData.addProperty("gameMode", gameMode);
                }
                playerData.addProperty("isOp", player.isOp());
            } else if (proxyBridge != null) {
                // Proxy: show which backend server the player is on + cached data
                String serverName = player.getConnectedServer();
                if (serverName != null) {
                    playerData.addProperty("server", serverName);

                    // Enrich with backend cached data
                    JsonObject cached = getCachedPlayerData(serverName, player.getUniqueId().toString());
                    if (cached != null) {
                        copyIfPresent(cached, playerData, "world");
                        copyIfPresent(cached, playerData, "gameMode");
                        copyIfPresent(cached, playerData, "health");
                        copyIfPresent(cached, playerData, "maxHealth");
                        copyIfPresent(cached, playerData, "level");
                    }
                }
            }
            
            players.add(playerData);
        }

        JsonObject data = new JsonObject();
        data.addProperty("count", players.size());
        data.add("players", players);

        return Response.success(data);
    }

    /**
     * 获取玩家详细信息
     */
    private Response getPlayerInfo(FullHttpRequest request) {
        if (request.method() != HttpMethod.GET) {
            return Response.error(ErrorCode.REQUEST_PARAM_ERROR, "仅支持GET请求");
        }

        // 从路径中提取玩家名称
        String path = request.uri();
        int queryIndex = path.indexOf('?');
        if (queryIndex > 0) {
            path = path.substring(0, queryIndex);
        }
        
        String playerName = path.substring("/api/v1/players/".length());
        if (playerName.isEmpty()) {
            return Response.error(ErrorCode.REQUEST_PARAM_MISSING, "缺少玩家名称");
        }

        Optional<CommonPlayer> playerOpt = platformAdapter.getPlayer(playerName);
        
        if (playerOpt.isEmpty()) {
            return Response.error(ErrorCode.PLAYER_NOT_ONLINE, "玩家不在线: " + playerName);
        }

        CommonPlayer player = playerOpt.get();
        JsonObject data = new JsonObject();
        
        // 基本信息
        data.addProperty("uuid", player.getUniqueId().toString());
        data.addProperty("name", player.getName());
        data.addProperty("displayName", player.getDisplayName());
        data.addProperty("ping", player.getPing());
        data.addProperty("online", player.isOnline());

        // 后端服务器特有信息
        if (platformAdapter.getPlatformType().isBackend()) {
            addBackendPlayerDetails(data, player);
        }

        // 代理端：从后端实时获取详细信息
        if (platformAdapter.getPlatformType().isProxy()) {
            String serverName = player.getConnectedServer();
            if (serverName != null) {
                data.addProperty("server", serverName);

                // Request fresh player data from backend and wait
                if (proxyBridge != null) {
                    String uuid = player.getUniqueId().toString();
                    try {
                        CompletableFuture<JsonObject> freshDataFuture =
                                proxyBridge.requestAndAwaitPlayerData(serverName, uuid);
                        freshDataFuture.join(); // Block until data arrives or timeout
                    } catch (Exception ignored) {
                        // Timeout or PMC failure — fall back to cached data
                    }
                    // Now read from cache (updated by the report callback, or stale as fallback)
                    JsonObject cached = getCachedPlayerData(serverName, uuid);
                    if (cached != null) {
                        // Copy all detailed fields from cached data
                        copyNumberIfPresent(cached, data, "health");
                        copyNumberIfPresent(cached, data, "maxHealth");
                        copyIfPresent(cached, data, "level");
                        copyIfPresent(cached, data, "gameMode");
                        copyIfPresent(cached, data, "world");
                        copyIfPresent(cached, data, "isOnline");
                        copyIfPresent(cached, data, "ping");

                        // Location from cached data
                        if (cached.has("location") && cached.get("location").isJsonObject()) {
                            data.add("location", cached.getAsJsonObject("location"));
                        }

                        // Additional fields the backend might have reported
                        copyIfPresent(cached, data, "foodLevel");
                        copyIfPresent(cached, data, "exp");
                        copyIfPresent(cached, data, "totalExp");
                        copyIfPresent(cached, data, "isOp");
                        copyIfPresent(cached, data, "isFlying");
                        copyIfPresent(cached, data, "firstPlayed");
                        copyIfPresent(cached, data, "lastPlayed");
                        copyIfPresent(cached, data, "onlineTime");
                        copyIfPresent(cached, data, "onlineTimeFormatted");
                    }
                }
            }
        }

        return Response.success(data);
    }

    /**
     * Add backend-specific detailed player fields.
     */
    private void addBackendPlayerDetails(JsonObject data, CommonPlayer player) {
        data.addProperty("health", player.getHealth());
        data.addProperty("maxHealth", player.getMaxHealth());
        data.addProperty("foodLevel", player.getFoodLevel());
        data.addProperty("level", player.getLevel());
        data.addProperty("exp", player.getExp());
        data.addProperty("totalExp", player.getTotalExp());
        data.addProperty("gameMode", player.getGameMode());
        data.addProperty("world", player.getWorld());
        data.addProperty("isOp", player.isOp());
        data.addProperty("isFlying", player.isFlying());
        data.addProperty("firstPlayed", player.getFirstPlayed());
        data.addProperty("lastPlayed", player.getLastPlayed());

        long onlineTime = player.getOnlineTime();
        if (onlineTime >= 0) {
            data.addProperty("onlineTime", onlineTime);
            data.addProperty("onlineTimeFormatted", formatDuration(onlineTime));
        }

        CommonPlayer.PlayerLocation loc = player.getLocation();
        if (loc != null) {
            JsonObject location = new JsonObject();
            location.addProperty("world", loc.getWorld());
            location.addProperty("x", Math.round(loc.getX() * 100.0) / 100.0);
            location.addProperty("y", Math.round(loc.getY() * 100.0) / 100.0);
            location.addProperty("z", Math.round(loc.getZ() * 100.0) / 100.0);
            data.add("location", location);
        }
    }

    /**
     * Get cached player data from backend via proxy bridge.
     */
    private JsonObject getCachedPlayerData(String serverName, String uuid) {
        if (proxyBridge == null) return null;

        BackendServerInfo backend = proxyBridge.getBackendServer(serverName);
        if (backend == null || !backend.isAuthenticated()) return null;

        return backend.getPlayerDataCache().get(uuid);
    }

    /**
     * Copy a JSON property if it exists.
     */
    private void copyIfPresent(JsonObject from, JsonObject to, String key) {
        if (from.has(key)) {
            to.add(key, from.get(key));
        }
    }

    /**
     * Copy a JSON number property if it exists.
     */
    private void copyNumberIfPresent(JsonObject from, JsonObject to, String key) {
        if (from.has(key) && from.get(key).isJsonPrimitive()) {
            to.add(key, from.get(key));
        }
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        long remainingSeconds = seconds % 60;
        long remainingMinutes = minutes % 60;
        long remainingHours = hours % 24;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0 || days > 0) {
            sb.append(remainingHours).append("h ");
        }
        sb.append(remainingMinutes).append("m ");
        sb.append(remainingSeconds).append("s");
        return sb.toString().trim();
    }
}
