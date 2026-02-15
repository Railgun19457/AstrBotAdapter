package io.github.railgun19457.astrbotadapter.communication.proxy;

import com.google.gson.JsonObject;

import java.util.Map;

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
}
