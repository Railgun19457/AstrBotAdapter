package io.github.railgun19457.astrbotadapter.communication.proxy;

import com.google.gson.JsonObject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import io.github.railgun19457.astrbotadapter.communication.MessageBroadcaster;
import io.github.railgun19457.astrbotadapter.communication.protocol.Message;
import io.github.railgun19457.astrbotadapter.communication.protocol.MessageType;
import io.github.railgun19457.astrbotadapter.core.config.PluginConfig;
import io.github.railgun19457.astrbotadapter.core.util.JsonUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Velocity-side proxy bridge for managing backend server connections.
 * Handles Plugin Messaging Channel communication with backend servers,
 * aggregates data from multiple backends, and forwards relevant events to Astrbot.
 */
public class VelocityProxyBridge {

    private static final String CHANNEL_NAMESPACE = "astrbot";
    private static final String CHANNEL_NAME = "proxy";
    private static final String SECRET_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SECRET_LENGTH = 32;
    private static final long BACKEND_TIMEOUT_MS = 120_000; // 2 minutes
    private static final String SECRET_FILE_NAME = "proxy-secret.txt";
    private static final long UNAUTH_WARN_INTERVAL_MS = 30_000L;

    private final Object plugin;
    private final ProxyServer proxy;
    private final PluginConfig config;
    private final Logger logger;
    private final Path dataFolder;

    private final ChannelIdentifier channelId;
    private String secret;

    // Registered backend servers: serverName → info
    private final Map<String, BackendServerInfo> backendServers = new ConcurrentHashMap<>();
    // Throttle unauthenticated backend warnings per server
    private final Map<String, Long> lastUnauthWarnTime = new ConcurrentHashMap<>();

    // Message broadcaster for forwarding to Astrbot via WebSocket
    private MessageBroadcaster broadcaster;

    // Handler for proxy-originated events (chat, join/quit, etc.)
    private Consumer<ProxyBridgeEvent> eventHandler;

    public VelocityProxyBridge(Object plugin, ProxyServer proxy, PluginConfig config,
                               Path dataFolder, Logger logger) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.config = config;
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.channelId = MinecraftChannelIdentifier.create(CHANNEL_NAMESPACE, CHANNEL_NAME);
    }

    /**
     * Initialize the proxy bridge: load or generate persistent secret, register channels.
     */
    public void initialize() {
        // Load existing secret or generate a new one
        this.secret = loadOrCreateSecret();

        // Register plugin messaging channel
        proxy.getChannelRegistrar().register(channelId);

        logger.info("========================================");
        logger.info("代理桥接模式已启用!");
        logger.info("后端服务器Secret: " + secret);
        logger.info("Secret已保存至: " + dataFolder.resolve(SECRET_FILE_NAME));
        logger.info("请将此Secret填入后端服务器的配置文件中");
        logger.info("========================================");

        // Schedule periodic cleanup of dead backends
        proxy.getScheduler().buildTask(plugin, this::cleanupDeadBackends)
                .delay(java.time.Duration.ofSeconds(30))
                .repeat(java.time.Duration.ofSeconds(60))
                .schedule();
    }

    /**
     * Load secret from file, or generate a new one and save it.
     * The secret is persisted in the plugin data folder so it survives restarts.
     */
    private String loadOrCreateSecret() {
        Path secretFile = dataFolder.resolve(SECRET_FILE_NAME);

        // Try to load existing secret
        if (Files.exists(secretFile)) {
            try {
                String loaded = Files.readString(secretFile, StandardCharsets.UTF_8).trim();
                if (!loaded.isEmpty()) {
                    logger.info("已从文件加载代理Secret");
                    return loaded;
                }
            } catch (IOException e) {
                logger.warning("读取Secret文件失败，将重新生成: " + e.getMessage());
            }
        }

        // Generate new secret and persist
        String newSecret = generateSecret();
        try {
            Files.createDirectories(dataFolder);
            Files.writeString(secretFile, newSecret, StandardCharsets.UTF_8);
            logger.info("已生成新的代理Secret并保存至文件");
        } catch (IOException e) {
            logger.severe("保存Secret文件失败: " + e.getMessage());
        }
        return newSecret;
    }

    /**
     * Shutdown the proxy bridge.
     */
    public void shutdown() {
        proxy.getChannelRegistrar().unregister(channelId);
        backendServers.clear();
        logger.info("代理桥接已关闭");
    }

    /**
     * Set the message broadcaster for forwarding events to Astrbot.
     */
    public void setBroadcaster(MessageBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    /**
     * Set the event handler for proxy bridge events.
     */
    public void setEventHandler(Consumer<ProxyBridgeEvent> eventHandler) {
        this.eventHandler = eventHandler;
    }

    /**
     * Handle incoming plugin messages from backend servers.
     */
    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!channelId.equals(event.getIdentifier())) {
            return;
        }

        // Only accept messages from server connections (backend → proxy)
        if (!(event.getSource() instanceof ServerConnection)) {
            return;
        }

        // Mark as handled so it's not forwarded
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        ServerConnection serverConn = (ServerConnection) event.getSource();
        String senderServer = serverConn.getServerInfo().getName();

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(event.getData()));
            String jsonStr = in.readUTF();
            ProxyMessage msg = ProxyMessage.fromJson(jsonStr);

            if (msg == null || msg.getType() == null) {
                logger.warning("Received invalid proxy message from " + senderServer);
                return;
            }

            handleBackendMessage(senderServer, serverConn, msg);
        } catch (IOException e) {
            logger.warning("Failed to read proxy message from " + senderServer + ": " + e.getMessage());
        }
    }

    /**
     * Process a message from a backend server.
     */
    private void handleBackendMessage(String senderServer, ServerConnection serverConn, ProxyMessage msg) {
        // If backend timed out and was removed, it may still think it's authenticated.
        // Force it to re-authenticate when any non-auth message arrives.
        if (msg.getType() != ProxyMessageType.AUTH_REQUEST && !isBackendAuthenticated(senderServer)) {
            sendAuthResponse(serverConn, false, "Not authenticated, please re-authenticate");
            warnUnauthenticatedBackend(senderServer, msg.getType().name());
            return;
        }

        switch (msg.getType()) {
            case AUTH_REQUEST -> handleAuthRequest(senderServer, serverConn, msg);
            case SERVER_INFO_REPORT -> handleServerInfoReport(senderServer, msg);
            case PLAYER_DATA_REPORT -> handlePlayerDataReport(senderServer, msg);
            case CHAT_MESSAGE_REPORT -> handleChatMessageReport(senderServer, msg);
            case AI_CHAT_REQUEST_REPORT -> handleAiChatRequestReport(senderServer, msg);
            case PLAYER_JOIN_REPORT -> handlePlayerJoinReport(senderServer, msg);
            case PLAYER_QUIT_REPORT -> handlePlayerQuitReport(senderServer, msg);
            case COMMAND_RESULT_REPORT -> handleCommandResultReport(senderServer, msg);
            case LOG_REPORT -> handleLogReport(senderServer, msg);
            default -> logger.warning("Unknown proxy message type from " + senderServer + ": " + msg.getType());
        }
    }

    // ===== Message Handlers =====

    private void handleAuthRequest(String senderServer, ServerConnection serverConn, ProxyMessage msg) {
        JsonObject data = msg.getData();
        if (data == null) {
            sendAuthResponse(serverConn, false, "Missing auth data");
            return;
        }

        String receivedSecret = data.has("secret") ? data.get("secret").getAsString() : "";
        // Always use the Velocity-registered server name as the canonical key,
        // since all subsequent plugin messages will be keyed by this name.
        // The backend-reported name is only used for display purposes.
        String backendReportedName = data.has("serverName") ? data.get("serverName").getAsString() : senderServer;

        if (!this.secret.equals(receivedSecret)) {
            sendAuthResponse(serverConn, false, "Invalid secret");
            logger.warning("后端服务器 " + senderServer + " 认证失败: Secret不匹配");
            return;
        }

        // Create or update backend server info, keyed by Velocity server name
        BackendServerInfo info = backendServers.computeIfAbsent(senderServer, BackendServerInfo::new);
        info.setAuthenticated(true);

        // Update with initial info from auth request
        if (data.has("platform") || data.has("version")) {
            info.updateFromReport(data);
        }

        sendAuthResponse(serverConn, true, "认证成功");
        logger.info("后端服务器 " + senderServer + " (" + backendReportedName + ") 已通过认证 [" +
                (data.has("platform") ? data.get("platform").getAsString() : "Unknown") + "]");

        // Sync configuration to backend after successful auth
        sendConfigSync(serverConn);
    }

    /**
     * Send aiChat configuration to a backend server.
     * This ensures all AI chat settings are managed centrally on the proxy.
     */
    private void sendConfigSync(ServerConnection serverConn) {
        JsonObject data = new JsonObject();

        // AI Chat config
        JsonObject aiChat = new JsonObject();

        JsonObject group = new JsonObject();
        group.addProperty("enabled", config.isGroupChatEnabled());
        group.addProperty("prefix", config.getGroupChatPrefix());
        aiChat.add("group", group);

        JsonObject priv = new JsonObject();
        priv.addProperty("enabled", config.isPrivateChatEnabled());
        priv.addProperty("prefix", config.getPrivateChatPrefix());
        priv.addProperty("echoFormat", config.getPrivateChatEchoFormat());
        aiChat.add("private", priv);

        aiChat.addProperty("responseFormat", config.getAiResponseFormat());
        aiChat.addProperty("thinkingMessage", config.getAiThinkingMessage());
        aiChat.addProperty("showThinking", config.isAiShowThinking());
        aiChat.addProperty("timeout", config.getAiTimeoutSeconds());

        data.add("aiChat", aiChat);

        ProxyMessage msg = ProxyMessage.builder()
                .type(ProxyMessageType.SYNC_CONFIG)
                .data(data)
                .build();

        sendToBackend(serverConn, msg);
        logger.info("已向后端服务器同步AI聊天配置");
    }

    private void sendAuthResponse(ServerConnection serverConn, boolean success, String message) {
        JsonObject data = new JsonObject();
        data.addProperty("success", success);
        data.addProperty("message", message);

        ProxyMessage response = ProxyMessage.builder()
                .type(ProxyMessageType.AUTH_RESPONSE)
                .data(data)
                .build();

        sendToBackend(serverConn, response);
    }

    private void handleServerInfoReport(String senderServer, ProxyMessage msg) {
        BackendServerInfo info = getAuthenticatedBackend(senderServer);
        if (info == null) return;

        info.updateFromReport(msg.getData());
    }

    private void handlePlayerDataReport(String senderServer, ProxyMessage msg) {
        BackendServerInfo info = getAuthenticatedBackend(senderServer);
        if (info == null) return;

        JsonObject data = msg.getData();
        if (data != null && data.has("uuid")) {
            info.updatePlayerData(data.get("uuid").getAsString(), data);
        }
    }

    private void handleChatMessageReport(String senderServer, ProxyMessage msg) {
        BackendServerInfo info = getAuthenticatedBackend(senderServer);
        if (info == null) return;

        // Fire event so that AstrbotAdapterVelocity can handle message forwarding
        if (eventHandler != null) {
            eventHandler.accept(new ProxyBridgeEvent(
                    ProxyBridgeEvent.Type.CHAT_MESSAGE, senderServer, msg.getData()));
        }
    }

    private void handleAiChatRequestReport(String senderServer, ProxyMessage msg) {
        BackendServerInfo info = getAuthenticatedBackend(senderServer);
        if (info == null) return;

        // Backend has processed the AI chat trigger locally (prefix stripped, private chat
        // cancelled + echo sent). Forward to Velocity for routing to Astrbot via WS.
        if (eventHandler != null) {
            eventHandler.accept(new ProxyBridgeEvent(
                    ProxyBridgeEvent.Type.AI_CHAT_REQUEST, senderServer, msg.getData()));
        }
    }

    private void handlePlayerJoinReport(String senderServer, ProxyMessage msg) {
        BackendServerInfo info = getAuthenticatedBackend(senderServer);
        if (info == null) return;

        if (eventHandler != null) {
            eventHandler.accept(new ProxyBridgeEvent(
                    ProxyBridgeEvent.Type.PLAYER_JOIN, senderServer, msg.getData()));
        }
    }

    private void handlePlayerQuitReport(String senderServer, ProxyMessage msg) {
        BackendServerInfo info = getAuthenticatedBackend(senderServer);
        if (info == null) return;

        // Remove player from cache
        JsonObject data = msg.getData();
        if (data != null && data.has("playerUuid")) {
            info.removePlayer(data.get("playerUuid").getAsString());
        }

        if (eventHandler != null) {
            eventHandler.accept(new ProxyBridgeEvent(
                    ProxyBridgeEvent.Type.PLAYER_QUIT, senderServer, msg.getData()));
        }
    }

    private void handleCommandResultReport(String senderServer, ProxyMessage msg) {
        BackendServerInfo info = getAuthenticatedBackend(senderServer);
        if (info == null) return;

        // Forward command result to Astrbot via WebSocket
        if (broadcaster != null && broadcaster.isRunning() && msg.getData() != null) {
            JsonObject payload = msg.getData();
            payload.addProperty("serverName", senderServer);

            Message response = Message.builder()
                    .type(MessageType.COMMAND_RESPONSE)
                    .replyTo(msg.getReplyTo())
                    .payload(payload)
                    .build();

            broadcaster.broadcast(response);
        }
    }

    private void handleLogReport(String senderServer, ProxyMessage msg) {
        BackendServerInfo info = getAuthenticatedBackend(senderServer);
        if (info == null) return;

        // Forward log data to the event handler
        if (eventHandler != null) {
            eventHandler.accept(new ProxyBridgeEvent(
                    ProxyBridgeEvent.Type.LOG_REPORT, senderServer, msg.getData()));
        }
    }

    // ===== Outbound Commands to Backend =====

    /**
     * Send a command to a specific backend server for execution.
     */
    public boolean sendCommandToBackend(String serverName, String command,
                                        String executor, String playerUuid, String requestId) {
        BackendServerInfo info = backendServers.get(serverName);
        if (info == null || !info.isAuthenticated()) {
            return false;
        }

        JsonObject data = new JsonObject();
        data.addProperty("command", command);
        data.addProperty("executor", executor);
        if (playerUuid != null) {
            data.addProperty("playerUuid", playerUuid);
        }

        ProxyMessage msg = ProxyMessage.builder()
                .type(ProxyMessageType.EXECUTE_COMMAND)
                .id(requestId)
                .data(data)
                .build();

        return sendToServer(serverName, msg);
    }

    /**
     * Send a private message to a player on a specific backend server.
     */
    public boolean sendMessageToBackend(String serverName, String playerUuid,
                                        String playerName, String message) {
        JsonObject data = new JsonObject();
        if (playerUuid != null) data.addProperty("playerUuid", playerUuid);
        if (playerName != null) data.addProperty("playerName", playerName);
        data.addProperty("message", message);

        ProxyMessage msg = ProxyMessage.builder()
                .type(ProxyMessageType.SEND_MESSAGE)
                .data(data)
                .build();

        return sendToServer(serverName, msg);
    }

    /**
     * Broadcast a message to all authenticated backend servers.
     */
    public void broadcastToAllBackends(String message) {
        JsonObject data = new JsonObject();
        data.addProperty("message", message);

        ProxyMessage msg = ProxyMessage.builder()
                .type(ProxyMessageType.BROADCAST_MESSAGE)
                .data(data)
                .build();

        for (String serverName : backendServers.keySet()) {
            BackendServerInfo info = backendServers.get(serverName);
            if (info != null && info.isAuthenticated()) {
                sendToServer(serverName, msg);
            }
        }
    }

    /**
     * Request server info from a specific backend.
     */
    public void requestServerInfo(String serverName) {
        ProxyMessage msg = ProxyMessage.builder()
                .type(ProxyMessageType.REQUEST_SERVER_INFO)
                .build();

        sendToServer(serverName, msg);
    }

    /**
     * Request player data from a specific backend.
     */
    public void requestPlayerData(String serverName, String playerUuid) {
        JsonObject data = new JsonObject();
        if (playerUuid != null) {
            data.addProperty("playerUuid", playerUuid);
        }

        ProxyMessage msg = ProxyMessage.builder()
                .type(ProxyMessageType.REQUEST_PLAYER_DATA)
                .data(data)
                .build();

        sendToServer(serverName, msg);
    }

    // ===== Utility =====

    /**
     * Send a ProxyMessage to a specific backend server.
     */
    private boolean sendToServer(String serverName, ProxyMessage msg) {
        Optional<RegisteredServer> serverOpt = proxy.getServer(serverName);
        if (serverOpt.isEmpty()) {
            return false;
        }

        RegisteredServer server = serverOpt.get();
        Collection<Player> players = server.getPlayersConnected();
        if (players.isEmpty()) {
            return false; // No players on that server to relay the message
        }

        try {
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteOut);
            out.writeUTF(msg.toJson());

            byte[] data = byteOut.toByteArray();
            if (data.length > ProxyChannel.MAX_PAYLOAD_SIZE) {
                logger.warning("Proxy message too large (" + data.length + " bytes), dropping");
                return false;
            }

            // Use the first player's connection to send
            Player player = players.iterator().next();
            player.getCurrentServer().ifPresent(conn ->
                    conn.sendPluginMessage(channelId, data));

            return true;
        } catch (IOException e) {
            logger.warning("Failed to send message to backend " + serverName + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Send a message directly to a ServerConnection.
     */
    private void sendToBackend(ServerConnection serverConn, ProxyMessage msg) {
        try {
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteOut);
            out.writeUTF(msg.toJson());

            serverConn.sendPluginMessage(channelId, byteOut.toByteArray());
        } catch (IOException e) {
            logger.warning("Failed to send message to backend: " + e.getMessage());
        }
    }

    /**
     * Get an authenticated backend server info, or null if not authenticated.
     */
    private BackendServerInfo getAuthenticatedBackend(String serverName) {
        BackendServerInfo info = backendServers.get(serverName);
        if (info == null || !info.isAuthenticated()) {
            return null;
        }
        return info;
    }

    private boolean isBackendAuthenticated(String serverName) {
        BackendServerInfo info = backendServers.get(serverName);
        return info != null && info.isAuthenticated();
    }

    private void warnUnauthenticatedBackend(String serverName, String messageType) {
        long now = System.currentTimeMillis();
        long last = lastUnauthWarnTime.getOrDefault(serverName, 0L);
        if (now - last >= UNAUTH_WARN_INTERVAL_MS) {
            logger.warning("Received message from unauthenticated backend: " + serverName
                    + " (type=" + messageType + "), requesting re-authentication");
            lastUnauthWarnTime.put(serverName, now);
        }
    }

    /**
     * Clean up backends that haven't reported in a while.
     */
    private void cleanupDeadBackends() {
        backendServers.entrySet().removeIf(entry -> {
            if (!entry.getValue().isAlive(BACKEND_TIMEOUT_MS)) {
                logger.info("后端服务器 " + entry.getKey() + " 超时，已移除");
                lastUnauthWarnTime.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Generate a random secret string.
     */
    private String generateSecret() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(SECRET_LENGTH);
        for (int i = 0; i < SECRET_LENGTH; i++) {
            sb.append(SECRET_CHARS.charAt(random.nextInt(SECRET_CHARS.length())));
        }
        return sb.toString();
    }

    // ===== Getters =====

    /**
     * Get all connected backend servers.
     */
    public Map<String, BackendServerInfo> getBackendServers() {
        return backendServers;
    }

    /**
     * Get a specific backend server info.
     */
    public BackendServerInfo getBackendServer(String serverName) {
        return backendServers.get(serverName);
    }

    /**
     * Get the number of authenticated backends.
     */
    public int getAuthenticatedBackendCount() {
        return (int) backendServers.values().stream()
                .filter(BackendServerInfo::isAuthenticated)
                .count();
    }

    /**
     * Get the generated secret.
     */
    public String getSecret() {
        return secret;
    }

    /**
     * Get the channel identifier.
     */
    public ChannelIdentifier getChannelId() {
        return channelId;
    }

    // ===== Inner Event Class =====

    /**
     * Event fired by the proxy bridge for processing by the main plugin.
     */
    public static class ProxyBridgeEvent {

        public enum Type {
            CHAT_MESSAGE,
            AI_CHAT_REQUEST,
            PLAYER_JOIN,
            PLAYER_QUIT,
            LOG_REPORT
        }

        private final Type type;
        private final String serverName;
        private final JsonObject data;

        public ProxyBridgeEvent(Type type, String serverName, JsonObject data) {
            this.type = type;
            this.serverName = serverName;
            this.data = data;
        }

        public Type getType() {
            return type;
        }

        public String getServerName() {
            return serverName;
        }

        public JsonObject getData() {
            return data;
        }
    }
}
