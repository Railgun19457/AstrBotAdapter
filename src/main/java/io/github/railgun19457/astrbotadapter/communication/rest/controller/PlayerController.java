package io.github.railgun19457.astrbotadapter.communication.rest.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
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
import io.netty.handler.codec.http.QueryStringDecoder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

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

        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        boolean detail = getBooleanParam(decoder, "detail", false);
        boolean includeOffline = getBooleanParam(decoder, "includeOffline", false);

        final boolean listDetail = detail;
        final boolean listIncludeOffline = includeOffline;

        JsonObject data = runOnServerThread(() -> {
            Map<String, JsonObject> mergedPlayers = new LinkedHashMap<>();

            for (CommonPlayer player : platformAdapter.getOnlinePlayers()) {
                JsonObject playerData = buildLivePlayerPayload(player);

                if (proxyBridge != null) {
                    String serverName = getString(playerData, "server");
                    JsonObject cached = getCachedPlayerData(serverName, player.getUniqueId().toString());
                    if (cached != null) {
                        enrichFromCached(playerData, cached, false);
                    }
                }

                mergedPlayers.put(player.getUniqueId().toString(), playerData);
            }

            if (listIncludeOffline && proxyBridge != null) {
                appendCachedOfflinePlayers(mergedPlayers);
            }

            JsonArray players = new JsonArray();
            for (JsonObject player : mergedPlayers.values()) {
                players.add(listDetail ? player : toPlayerSummary(player));
            }

            JsonObject payload = new JsonObject();
            payload.addProperty("count", players.size());
            payload.add("players", players);
            return payload;
        });
        return Response.success(data);
    }

    /**
     * 获取玩家详细信息
     */
    private Response getPlayerInfo(FullHttpRequest request) {
        if (request.method() != HttpMethod.GET) {
            return Response.error(ErrorCode.REQUEST_PARAM_ERROR, "仅支持GET请求");
        }

        String path = request.uri();
        int queryIndex = path.indexOf('?');
        if (queryIndex > 0) {
            path = path.substring(0, queryIndex);
        }

        String identifier = path.substring("/api/v1/players/".length());
        identifier = URLDecoder.decode(identifier, StandardCharsets.UTF_8);
        if (identifier.isBlank()) {
            return Response.error(ErrorCode.REQUEST_PARAM_MISSING, "缺少玩家名称");
        }
        final String requestedIdentifier = identifier;

        JsonObject responseData = runOnServerThread(() -> {
            JsonObject playerData = resolvePlayerPayload(requestedIdentifier);
            if (playerData == null) {
                return null;
            }
            return playerData;
        });

        if (responseData == null) {
            return Response.error(ErrorCode.RESOURCE_NOT_FOUND, "未找到玩家: " + requestedIdentifier);
        }

        return Response.success(responseData);
    }

    private JsonObject toPlayerSummary(JsonObject full) {
        JsonObject summary = new JsonObject();
        copyIfPresent(full, summary, "uuid");
        copyIfPresent(full, summary, "name");
        copyIfPresent(full, summary, "displayName");
        copyIfPresent(full, summary, "online");
        copyIfPresent(full, summary, "server");
        copyIfPresent(full, summary, "ping");
        copyIfPresent(full, summary, "dataSource");
        return summary;
    }

    private JsonObject resolvePlayerPayload(String identifier) {
        Optional<CommonPlayer> onlinePlayer = findOnlinePlayer(identifier);
        if (onlinePlayer.isPresent()) {
            CommonPlayer player = onlinePlayer.get();
            JsonObject playerData = buildLivePlayerPayload(player);

            if (platformAdapter.getPlatformType().isProxy() && proxyBridge != null) {
                String serverName = getString(playerData, "server");
                String uuid = getString(playerData, "uuid");

                if (serverName != null && uuid != null) {
                    try {
                        CompletableFuture<JsonObject> freshDataFuture = proxyBridge.requestAndAwaitPlayerData(serverName, uuid);
                        freshDataFuture.join();
                    } catch (Exception ignored) {
                    }

                    JsonObject cached = getCachedPlayerData(serverName, uuid);
                    if (cached != null) {
                        enrichFromCached(playerData, cached, false);
                    }
                }
            }

            playerData.addProperty("dataSource", "live");
            return playerData;
        }

        CachedLookup cachedLookup = findCachedPlayer(identifier);
        if (cachedLookup != null) {
            return buildCachedPlayerPayload(cachedLookup.serverName, cachedLookup.playerData, true);
        }

        return buildBackendPersistedPlayerPayload(identifier);
    }

    private JsonObject getCachedPlayerData(String serverName, String uuid) {
        if (proxyBridge == null || serverName == null || uuid == null) {
            return null;
        }

        BackendServerInfo backend = proxyBridge.getBackendServer(serverName);
        if (backend == null || !backend.isAuthenticated()) {
            return null;
        }

        return backend.getPlayerDataCache().get(uuid);
    }

    private void appendCachedOfflinePlayers(Map<String, JsonObject> mergedPlayers) {
        for (Map.Entry<String, BackendServerInfo> backendEntry : proxyBridge.getBackendServers().entrySet()) {
            String serverName = backendEntry.getKey();
            BackendServerInfo info = backendEntry.getValue();

            if (info == null || !info.isAuthenticated()) {
                continue;
            }

            for (Map.Entry<String, JsonObject> playerEntry : info.getPlayerDataCache().entrySet()) {
                String uuid = playerEntry.getKey();
                if (mergedPlayers.containsKey(uuid)) {
                    continue;
                }
                JsonObject cachedData = playerEntry.getValue();
                if (cachedData == null) {
                    continue;
                }
                mergedPlayers.put(uuid, buildCachedPlayerPayload(serverName, cachedData, true));
            }
        }
    }

    private Optional<CommonPlayer> findOnlinePlayer(String identifier) {
        UUID uuid = parseUuid(identifier);
        if (uuid != null) {
            return platformAdapter.getPlayer(uuid);
        }
        return platformAdapter.getPlayer(identifier);
    }

    private JsonObject buildLivePlayerPayload(CommonPlayer player) {
        JsonObject payload = createPlayerSkeleton();

        payload.addProperty("uuid", player.getUniqueId().toString());
        payload.addProperty("name", player.getName());
        payload.addProperty("displayName", player.getDisplayName() == null ? player.getName() : player.getDisplayName());
        payload.addProperty("online", player.isOnline());
        payload.addProperty("ping", player.getPing());

        String serverName = platformAdapter.getPlatformType().isProxy()
                ? player.getConnectedServer()
                : platformAdapter.getServerName();
        if (serverName != null) {
            payload.addProperty("server", serverName);
            payload.addProperty("lastKnownServer", serverName);
        }

        if (platformAdapter.getPlatformType().isBackend()) {
            addBackendLiveDetails(payload, player);
        }

        payload.addProperty("dataSource", "live");
        return payload;
    }

    private void addBackendLiveDetails(JsonObject payload, CommonPlayer player) {
        if (player.getHealth() >= 0) payload.addProperty("health", player.getHealth());
        if (player.getMaxHealth() >= 0) payload.addProperty("maxHealth", player.getMaxHealth());
        if (player.getLevel() >= 0) payload.addProperty("level", player.getLevel());
        if (player.getFoodLevel() >= 0) payload.addProperty("foodLevel", player.getFoodLevel());
        if (player.getExp() >= 0) payload.addProperty("exp", player.getExp());
        if (player.getTotalExp() >= 0) payload.addProperty("totalExp", player.getTotalExp());

        if (player.getWorld() != null) payload.addProperty("world", player.getWorld());
        if (player.getGameMode() != null) payload.addProperty("gameMode", player.getGameMode());

        payload.addProperty("isOp", player.isOp());
        payload.addProperty("isFlying", player.isFlying());

        if (player.getFirstPlayed() > 0) payload.addProperty("firstPlayed", player.getFirstPlayed());
        if (player.getLastPlayed() > 0) payload.addProperty("lastPlayed", player.getLastPlayed());

        long onlineTime = player.getOnlineTime();
        if (onlineTime >= 0) {
            payload.addProperty("onlineTime", onlineTime);
            payload.addProperty("onlineTimeFormatted", formatDuration(onlineTime));
        }

        CommonPlayer.PlayerLocation location = player.getLocation();
        if (location != null) {
            JsonObject locationJson = new JsonObject();
            locationJson.addProperty("world", location.getWorld());
            locationJson.addProperty("x", Math.round(location.getX() * 100.0) / 100.0);
            locationJson.addProperty("y", Math.round(location.getY() * 100.0) / 100.0);
            locationJson.addProperty("z", Math.round(location.getZ() * 100.0) / 100.0);
            payload.add("location", locationJson);
        }
    }

    private JsonObject buildCachedPlayerPayload(String serverName, JsonObject cached, boolean forceOffline) {
        JsonObject payload = createPlayerSkeleton();

        copyIfPresent(cached, payload, "uuid");
        copyIfPresent(cached, payload, "name");
        copyIfPresent(cached, payload, "displayName");

        if (serverName != null) {
            payload.addProperty("server", serverName);
            payload.addProperty("lastKnownServer", serverName);
        }

        enrichFromCached(payload, cached, forceOffline);
        payload.addProperty("dataSource", "cache");
        return payload;
    }

    private void enrichFromCached(JsonObject target, JsonObject cached, boolean forceOffline) {
        copyIfPresent(cached, target, "ping");
        copyIfPresent(cached, target, "world");
        copyIfPresent(cached, target, "gameMode");
        copyIfPresent(cached, target, "health");
        copyIfPresent(cached, target, "maxHealth");
        copyIfPresent(cached, target, "level");
        copyIfPresent(cached, target, "foodLevel");
        copyIfPresent(cached, target, "exp");
        copyIfPresent(cached, target, "totalExp");
        copyIfPresent(cached, target, "isOp");
        copyIfPresent(cached, target, "isFlying");
        copyIfPresent(cached, target, "firstPlayed");
        copyIfPresent(cached, target, "lastPlayed");
        copyIfPresent(cached, target, "onlineTime");
        copyIfPresent(cached, target, "onlineTimeFormatted");

        if (cached.has("location") && cached.get("location").isJsonObject()) {
            target.add("location", cached.getAsJsonObject("location"));
        }

        if (cached.has("server") && !cached.get("server").isJsonNull()) {
            target.add("lastKnownServer", cached.get("server"));
            if (target.has("server") && target.get("server").isJsonNull()) {
                target.add("server", cached.get("server"));
            }
        }

        if (forceOffline) {
            target.addProperty("online", false);
        } else if (cached.has("isOnline") && !cached.get("isOnline").isJsonNull()) {
            target.addProperty("online", cached.get("isOnline").getAsBoolean());
        }
    }

    private CachedLookup findCachedPlayer(String identifier) {
        if (proxyBridge == null) {
            return null;
        }

        UUID requestedUuid = parseUuid(identifier);
        String requestedName = identifier.trim();

        for (Map.Entry<String, BackendServerInfo> backendEntry : proxyBridge.getBackendServers().entrySet()) {
            String serverName = backendEntry.getKey();
            BackendServerInfo info = backendEntry.getValue();

            if (info == null || !info.isAuthenticated()) {
                continue;
            }

            for (Map.Entry<String, JsonObject> playerEntry : info.getPlayerDataCache().entrySet()) {
                String cacheUuid = playerEntry.getKey();
                JsonObject cachedData = playerEntry.getValue();
                if (cachedData == null) {
                    continue;
                }

                if (requestedUuid != null) {
                    String cachedUuidField = getString(cachedData, "uuid");
                    if (requestedUuid.toString().equalsIgnoreCase(cacheUuid)
                            || requestedUuid.toString().equalsIgnoreCase(cachedUuidField)) {
                        return new CachedLookup(serverName, cachedData);
                    }
                    continue;
                }

                String cachedName = getString(cachedData, "name");
                if (cachedName != null && cachedName.equalsIgnoreCase(requestedName)) {
                    return new CachedLookup(serverName, cachedData);
                }
            }
        }

        return null;
    }

    private JsonObject buildBackendPersistedPlayerPayload(String identifier) {
        if (!platformAdapter.getPlatformType().isBackend()) {
            return null;
        }

        try {
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
            Object offlinePlayer = null;
            UUID uuid = parseUuid(identifier);

            if (uuid != null) {
                offlinePlayer = bukkitClass.getMethod("getOfflinePlayer", UUID.class).invoke(null, uuid);
            }
            if (offlinePlayer == null) {
                offlinePlayer = bukkitClass.getMethod("getOfflinePlayer", String.class).invoke(null, identifier);
            }
            if (offlinePlayer == null) {
                return null;
            }

            boolean hasPlayedBefore = invokeBoolean(offlinePlayer, "hasPlayedBefore", false);
            boolean online = invokeBoolean(offlinePlayer, "isOnline", false);
            String name = invokeString(offlinePlayer, "getName");
            Object uuidObj = offlinePlayer.getClass().getMethod("getUniqueId").invoke(offlinePlayer);
            String offlineUuid = uuidObj == null ? null : uuidObj.toString();

            if (!online && !hasPlayedBefore && (name == null || name.isBlank())) {
                return null;
            }

            JsonObject payload = createPlayerSkeleton();
            putStringOrNull(payload, "uuid", offlineUuid);
            putStringOrNull(payload, "name", name == null || name.isBlank() ? identifier : name);
            putStringOrNull(payload, "displayName", name == null || name.isBlank() ? identifier : name);
            payload.addProperty("online", online);

            payload.addProperty("server", platformAdapter.getServerName());
            payload.addProperty("lastKnownServer", platformAdapter.getServerName());

            long firstPlayed = invokeLong(offlinePlayer, "getFirstPlayed", -1L);
            long lastPlayed = invokeLong(offlinePlayer, "getLastPlayed", -1L);
            if (firstPlayed > 0) payload.addProperty("firstPlayed", firstPlayed);
            if (lastPlayed > 0) payload.addProperty("lastPlayed", lastPlayed);

            if (offlinePlayer.getClass().getMethod("isOp") != null) {
                payload.addProperty("isOp", invokeBoolean(offlinePlayer, "isOp", false));
            }
            payload.addProperty("dataSource", "persisted");
            return payload;
        } catch (Exception ignored) {
            return null;
        }
    }

    private JsonObject createPlayerSkeleton() {
        JsonObject payload = new JsonObject();
        payload.add("uuid", JsonNull.INSTANCE);
        payload.add("name", JsonNull.INSTANCE);
        payload.add("displayName", JsonNull.INSTANCE);
        payload.addProperty("online", false);
        payload.add("server", JsonNull.INSTANCE);
        payload.add("lastKnownServer", JsonNull.INSTANCE);
        payload.add("ping", JsonNull.INSTANCE);
        payload.add("world", JsonNull.INSTANCE);
        payload.add("gameMode", JsonNull.INSTANCE);
        payload.add("health", JsonNull.INSTANCE);
        payload.add("maxHealth", JsonNull.INSTANCE);
        payload.add("level", JsonNull.INSTANCE);
        payload.add("foodLevel", JsonNull.INSTANCE);
        payload.add("exp", JsonNull.INSTANCE);
        payload.add("totalExp", JsonNull.INSTANCE);
        payload.add("isOp", JsonNull.INSTANCE);
        payload.add("isFlying", JsonNull.INSTANCE);
        payload.add("firstPlayed", JsonNull.INSTANCE);
        payload.add("lastPlayed", JsonNull.INSTANCE);
        payload.add("onlineTime", JsonNull.INSTANCE);
        payload.add("onlineTimeFormatted", JsonNull.INSTANCE);
        payload.add("location", JsonNull.INSTANCE);
        payload.addProperty("dataSource", "live");
        return payload;
    }

    private UUID parseUuid(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(input.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String getString(JsonObject jsonObject, String key) {
        if (jsonObject == null || !jsonObject.has(key) || jsonObject.get(key).isJsonNull()) {
            return null;
        }
        try {
            return jsonObject.get(key).getAsString();
        } catch (Exception ex) {
            return null;
        }
    }

    private void copyIfPresent(JsonObject from, JsonObject to, String key) {
        if (from != null && from.has(key)) {
            to.add(key, from.get(key));
        }
    }

    private void putStringOrNull(JsonObject to, String key, String value) {
        if (value == null) {
            to.add(key, JsonNull.INSTANCE);
        } else {
            to.addProperty(key, value);
        }
    }

    private boolean invokeBoolean(Object target, String methodName, boolean defaultValue) {
        try {
            Object result = target.getClass().getMethod(methodName).invoke(target);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
        } catch (Exception ignored) {
        }
        return defaultValue;
    }

    private long invokeLong(Object target, String methodName, long defaultValue) {
        try {
            Object result = target.getClass().getMethod(methodName).invoke(target);
            if (result instanceof Number) {
                return ((Number) result).longValue();
            }
        } catch (Exception ignored) {
        }
        return defaultValue;
    }

    private String invokeString(Object target, String methodName) {
        try {
            Object result = target.getClass().getMethod(methodName).invoke(target);
            if (result != null) {
                return result.toString();
            }
        } catch (Exception ignored) {
        }
        return null;
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
            throw new RuntimeException("获取玩家数据失败", e);
        }
    }

    private boolean getBooleanParam(QueryStringDecoder decoder, String key, boolean defaultValue) {
        var values = decoder.parameters().get(key);
        if (values == null || values.isEmpty()) {
            return defaultValue;
        }
        String raw = values.get(0);
        if (raw == null) {
            return defaultValue;
        }
        if ("1".equals(raw) || "yes".equalsIgnoreCase(raw) || "y".equalsIgnoreCase(raw)) {
            return true;
        }
        if ("0".equals(raw) || "no".equalsIgnoreCase(raw) || "n".equalsIgnoreCase(raw)) {
            return false;
        }
        return Boolean.parseBoolean(raw);
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

    private static class CachedLookup {
        private final String serverName;
        private final JsonObject playerData;

        private CachedLookup(String serverName, JsonObject playerData) {
            this.serverName = serverName;
            this.playerData = playerData;
        }
    }
}
