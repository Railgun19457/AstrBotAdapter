package io.github.railgun19457.astrbotadapter.communication.proxy;

import com.google.gson.JsonObject;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for proxy bridge functionality, used by REST controllers to
 * aggregate backend server data. This avoids direct dependency on Velocity classes
 * in shared code (controllers, UnifiedServer).
 */
public interface ProxyBridgeProvider {

    /**
     * Get all connected backend servers.
     */
    Map<String, BackendServerInfo> getBackendServers();

    /**
     * Get a specific backend server info.
     */
    BackendServerInfo getBackendServer(String serverName);

    /**
     * Get the number of authenticated backends.
     */
    int getAuthenticatedBackendCount();

    /**
     * Send a command to a specific backend server for execution.
     * @return true if the command was sent successfully
     */
    boolean sendCommandToBackend(String serverName, String command,
                                 String executor, String playerUuid, String requestId);

    /**
     * Request fresh player data from a backend server and wait for the response.
     * Sends REQUEST_PLAYER_DATA via PMC and waits for the PLAYER_DATA_REPORT callback.
     *
     * @param serverName the backend server name
     * @param playerUuid the player UUID to request data for
     * @return a CompletableFuture that completes with the player data JsonObject,
     *         or null if the request times out or fails
     */
    CompletableFuture<JsonObject> requestAndAwaitPlayerData(String serverName, String playerUuid);
}
