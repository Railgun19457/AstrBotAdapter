package io.github.railgun19457.astrbotadapter.communication.proxy;

import com.google.gson.JsonObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores aggregated information about a backend server connected via proxy bridge.
 */
public class BackendServerInfo {

    private final String serverName;
    private volatile boolean authenticated = false;
    private volatile long lastHeartbeat;

    // Server info
    private volatile String platform = "Unknown";
    private volatile String version = "Unknown";
    private volatile String motd = "";
    private volatile int onlineCount = 0;
    private volatile int maxPlayers = 0;
    private volatile long uptime = 0;
    private volatile JsonObject tps;
    private volatile JsonObject memory;

    // Player data cache: uuid → player data json
    private final Map<String, JsonObject> playerDataCache = new ConcurrentHashMap<>();

    public BackendServerInfo(String serverName) {
        this.serverName = serverName;
        this.lastHeartbeat = System.currentTimeMillis();
    }

    /**
     * Update server info from a SERVER_INFO_REPORT message.
     */
    public void updateFromReport(JsonObject data) {
        if (data == null) return;

        if (data.has("platform")) this.platform = data.get("platform").getAsString();
        if (data.has("version")) this.version = data.get("version").getAsString();
        if (data.has("motd")) this.motd = data.get("motd").getAsString();
        if (data.has("onlineCount")) this.onlineCount = data.get("onlineCount").getAsInt();
        if (data.has("maxPlayers")) this.maxPlayers = data.get("maxPlayers").getAsInt();
        if (data.has("uptime")) this.uptime = data.get("uptime").getAsLong();
        if (data.has("tps")) this.tps = data.getAsJsonObject("tps");
        if (data.has("memory")) this.memory = data.getAsJsonObject("memory");

        this.lastHeartbeat = System.currentTimeMillis();
    }

    /**
     * Update cached player data.
     */
    public void updatePlayerData(String uuid, JsonObject playerData) {
        playerDataCache.put(uuid, playerData);
        this.lastHeartbeat = System.currentTimeMillis();
    }

    /**
     * Remove a player from the cache.
     */
    public void removePlayer(String uuid) {
        playerDataCache.remove(uuid);
    }

    /**
     * Check if the server is still alive (received data recently).
     */
    public boolean isAlive(long timeoutMs) {
        return System.currentTimeMillis() - lastHeartbeat < timeoutMs;
    }

    /**
     * Convert to a JSON representation for REST API responses.
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("name", serverName);
        json.addProperty("platform", platform);
        json.addProperty("version", version);
        json.addProperty("motd", motd);
        json.addProperty("onlineCount", onlineCount);
        json.addProperty("maxPlayers", maxPlayers);
        json.addProperty("uptime", uptime);
        json.addProperty("authenticated", authenticated);
        if (tps != null) json.add("tps", tps);
        if (memory != null) json.add("memory", memory);
        return json;
    }

    // Getters and Setters

    public String getServerName() {
        return serverName;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public String getPlatform() {
        return platform;
    }

    public String getVersion() {
        return version;
    }

    public String getMotd() {
        return motd;
    }

    public int getOnlineCount() {
        return onlineCount;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public long getUptime() {
        return uptime;
    }

    public JsonObject getTps() {
        return tps;
    }

    public JsonObject getMemory() {
        return memory;
    }

    public Map<String, JsonObject> getPlayerDataCache() {
        return playerDataCache;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }
}
