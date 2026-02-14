package io.github.railgun19457.astrbotadapter.communication.proxy;

/**
 * Message types for proxy-backend plugin messaging communication.
 * These are internal message types used between the Velocity proxy and backend servers,
 * distinct from the WebSocket message types used for Astrbot communication.
 */
public enum ProxyMessageType {

    // ===== Handshake =====
    /** Backend → Proxy: Authentication request with secret */
    AUTH_REQUEST,
    /** Proxy → Backend: Authentication result */
    AUTH_RESPONSE,

    // ===== Data Reporting (Backend → Proxy) =====
    /** Backend reports server information (platform, version, TPS, memory, etc.) */
    SERVER_INFO_REPORT,
    /** Backend reports detailed player data (health, location, gamemode, etc.) */
    PLAYER_DATA_REPORT,
    /** Backend reports a chat message from a player */
    CHAT_MESSAGE_REPORT,
    /** Backend reports an AI chat request (already processed: prefix stripped, private chat cancelled) */
    AI_CHAT_REQUEST_REPORT,
    /** Backend reports player join event */
    PLAYER_JOIN_REPORT,
    /** Backend reports player quit event */
    PLAYER_QUIT_REPORT,
    /** Backend reports command execution result */
    COMMAND_RESULT_REPORT,
    /** Backend reports log entries */
    LOG_REPORT,

    // ===== Commands (Proxy → Backend) =====
    /** Proxy sends configuration (aiChat, etc.) to backend after auth success */
    SYNC_CONFIG,
    /** Proxy instructs backend to execute a command */
    EXECUTE_COMMAND,
    /** Proxy instructs backend to send a message to a player */
    SEND_MESSAGE,
    /** Proxy instructs backend to broadcast a message */
    BROADCAST_MESSAGE,
    /** Proxy requests server info from backend */
    REQUEST_SERVER_INFO,
    /** Proxy requests player data from backend */
    REQUEST_PLAYER_DATA,
    /** Proxy requests log entries from backend */
    REQUEST_LOGS;

    /**
     * Parse a ProxyMessageType from string.
     * @param value the type string
     * @return the parsed type, or null if not recognized
     */
    public static ProxyMessageType fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
