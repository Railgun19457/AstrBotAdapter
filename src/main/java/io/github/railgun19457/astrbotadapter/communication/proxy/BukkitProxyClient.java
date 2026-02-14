package io.github.railgun19457.astrbotadapter.communication.proxy;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.railgun19457.astrbotadapter.core.config.PluginConfig;
import io.github.railgun19457.astrbotadapter.core.util.JsonUtil;
import io.github.railgun19457.astrbotadapter.platform.PlatformAdapter;
import io.github.railgun19457.astrbotadapter.platform.common.CommonPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Backend proxy client for Bukkit/Paper/Folia servers.
 * Handles Plugin Messaging Channel communication with the Velocity proxy.
 * When proxy mode is enabled, this replaces the WebSocket/REST server.
 */
public class BukkitProxyClient implements PluginMessageListener {

    private final JavaPlugin plugin;
    private final PluginConfig config;
    private final PlatformAdapter platformAdapter;
    private final Logger logger;

    private volatile boolean authenticated = false;
    private Consumer<ProxyMessage> messageHandler;

    // Pending command results keyed by request ID
    private final ConcurrentHashMap<String, Consumer<JsonObject>> pendingCallbacks = new ConcurrentHashMap<>();

    public BukkitProxyClient(JavaPlugin plugin, PluginConfig config,
                             PlatformAdapter platformAdapter, Logger logger) {
        this.plugin = plugin;
        this.config = config;
        this.platformAdapter = platformAdapter;
        this.logger = logger;
    }

    /**
     * Initialize the proxy client: register plugin messaging channels.
     */
    public void initialize() {
        // Register outgoing channel
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, ProxyChannel.CHANNEL_ID);
        // Register incoming channel
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, ProxyChannel.CHANNEL_ID, this);

        logger.info("代理模式已启用，Plugin Messaging Channel已注册: " + ProxyChannel.CHANNEL_ID);

        // Schedule periodic server info reporting
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::reportServerInfo, 100L, 600L); // 30s interval
    }

    /**
     * Shutdown the proxy client: unregister channels.
     */
    public void shutdown() {
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, ProxyChannel.CHANNEL_ID);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, ProxyChannel.CHANNEL_ID);
        authenticated = false;
        logger.info("代理模式Plugin Messaging Channel已注销");
    }

    /**
     * Set the handler for incoming proxy messages.
     */
    public void setMessageHandler(Consumer<ProxyMessage> handler) {
        this.messageHandler = handler;
    }

    /**
     * Send authentication request to the proxy.
     */
    public void sendAuthRequest() {
        JsonObject data = new JsonObject();
        data.addProperty("secret", config.getProxySecret());
        data.addProperty("serverName", platformAdapter.getServerName());
        data.addProperty("platform", platformAdapter.getPlatformType().getDisplayName());
        data.addProperty("version", platformAdapter.getServerVersion());

        ProxyMessage msg = ProxyMessage.builder()
                .type(ProxyMessageType.AUTH_REQUEST)
                .serverName(platformAdapter.getServerName())
                .data(data)
                .build();

        sendMessage(msg);
        logger.info("已向代理端发送认证请求");
    }

    /**
     * Report server information to the proxy.
     */
    public void reportServerInfo() {
        if (!authenticated) {
            // Try to authenticate on each tick until successful
            sendAuthRequest();
            return;
        }

        JsonObject data = new JsonObject();
        data.addProperty("name", platformAdapter.getServerName());
        data.addProperty("platform", platformAdapter.getPlatformType().getDisplayName());
        data.addProperty("version", platformAdapter.getServerVersion());
        data.addProperty("motd", platformAdapter.getServerMotd());
        data.addProperty("onlineCount", platformAdapter.getOnlinePlayerCount());
        data.addProperty("maxPlayers", platformAdapter.getMaxPlayers());
        data.addProperty("uptime", platformAdapter.getServerUptime());

        // TPS (Bukkit-specific)
        try {
            double[] tps = platformAdapter.getServer().getTps();
            JsonObject tpsObj = new JsonObject();
            tpsObj.addProperty("tps1m", tps.length > 0 ? tps[0] : 20.0);
            tpsObj.addProperty("tps5m", tps.length > 1 ? tps[1] : 20.0);
            tpsObj.addProperty("tps15m", tps.length > 2 ? tps[2] : 20.0);
            data.add("tps", tpsObj);
        } catch (Exception ignored) {
        }

        // Memory
        Runtime runtime = Runtime.getRuntime();
        JsonObject memory = new JsonObject();
        memory.addProperty("used", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
        memory.addProperty("max", runtime.maxMemory() / (1024 * 1024));
        memory.addProperty("free", runtime.freeMemory() / (1024 * 1024));
        data.add("memory", memory);

        ProxyMessage msg = ProxyMessage.builder()
                .type(ProxyMessageType.SERVER_INFO_REPORT)
                .serverName(platformAdapter.getServerName())
                .data(data)
                .build();

        sendMessage(msg);
    }

    /**
     * Report a player's detailed data to the proxy.
     */
    public void reportPlayerData(CommonPlayer player) {
        if (!authenticated) return;

        JsonObject data = new JsonObject();
        data.addProperty("uuid", player.getUniqueId().toString());
        data.addProperty("name", player.getName());
        data.addProperty("displayName", player.getDisplayName());
        data.addProperty("health", player.getHealth());
        data.addProperty("maxHealth", player.getMaxHealth());
        data.addProperty("level", player.getLevel());
        data.addProperty("gameMode", player.getGameMode());
        data.addProperty("world", player.getWorld());
        data.addProperty("ping", player.getPing());
        data.addProperty("isOnline", player.isOnline());

        CommonPlayer.PlayerLocation loc = player.getLocation();
        if (loc != null) {
            JsonObject location = new JsonObject();
            location.addProperty("x", loc.getX());
            location.addProperty("y", loc.getY());
            location.addProperty("z", loc.getZ());
            data.add("location", location);
        }

        ProxyMessage msg = ProxyMessage.builder()
                .type(ProxyMessageType.PLAYER_DATA_REPORT)
                .serverName(platformAdapter.getServerName())
                .data(data)
                .build();

        sendMessage(msg);
    }

    /**
     * Report a chat message to the proxy.
     */
    public void reportChatMessage(UUID playerUuid, String playerName, String displayName, String message) {
        if (!authenticated) return;

        JsonObject data = new JsonObject();
        data.addProperty("playerUuid", playerUuid.toString());
        data.addProperty("playerName", playerName);
        data.addProperty("displayName", displayName);
        data.addProperty("message", message);

        ProxyMessage msg = ProxyMessage.builder()
                .type(ProxyMessageType.CHAT_MESSAGE_REPORT)
                .serverName(platformAdapter.getServerName())
                .data(data)
                .build();

        sendMessage(msg);
    }

    /**
     * Report player join event to the proxy.
     */
    public void reportPlayerJoin(UUID playerUuid, String playerName, String displayName) {
        if (!authenticated) return;

        JsonObject data = new JsonObject();
        data.addProperty("playerUuid", playerUuid.toString());
        data.addProperty("playerName", playerName);
        data.addProperty("displayName", displayName);
        data.addProperty("onlineCount", platformAdapter.getOnlinePlayerCount());
        data.addProperty("maxPlayers", platformAdapter.getMaxPlayers());

        ProxyMessage msg = ProxyMessage.builder()
                .type(ProxyMessageType.PLAYER_JOIN_REPORT)
                .serverName(platformAdapter.getServerName())
                .data(data)
                .build();

        sendMessage(msg);
    }

    /**
     * Report player quit event to the proxy.
     */
    public void reportPlayerQuit(UUID playerUuid, String playerName, String displayName, String reason) {
        if (!authenticated) return;

        JsonObject data = new JsonObject();
        data.addProperty("playerUuid", playerUuid.toString());
        data.addProperty("playerName", playerName);
        data.addProperty("displayName", displayName);
        data.addProperty("reason", reason);
        data.addProperty("onlineCount", platformAdapter.getOnlinePlayerCount());
        data.addProperty("maxPlayers", platformAdapter.getMaxPlayers());

        ProxyMessage msg = ProxyMessage.builder()
                .type(ProxyMessageType.PLAYER_QUIT_REPORT)
                .serverName(platformAdapter.getServerName())
                .data(data)
                .build();

        sendMessage(msg);
    }

    /**
     * Report command execution result to the proxy.
     */
    public void reportCommandResult(String replyTo, boolean success, String command,
                                    String output, long executionTime, List<String> logs) {
        if (!authenticated) return;

        JsonObject data = new JsonObject();
        data.addProperty("success", success);
        data.addProperty("command", command);
        data.addProperty("output", output);
        data.addProperty("executionTime", executionTime);
        if (logs != null && !logs.isEmpty()) {
            JsonArray logArray = new JsonArray();
            logs.forEach(logArray::add);
            data.add("logs", logArray);
        }

        ProxyMessage msg = ProxyMessage.builder()
                .type(ProxyMessageType.COMMAND_RESULT_REPORT)
                .replyTo(replyTo)
                .serverName(platformAdapter.getServerName())
                .data(data)
                .build();

        sendMessage(msg);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!ProxyChannel.CHANNEL_ID.equals(channel)) {
            return;
        }

        try {
            // Read the JSON string from the data stream
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            String jsonStr = in.readUTF();
            ProxyMessage proxyMsg = ProxyMessage.fromJson(jsonStr);

            if (proxyMsg == null || proxyMsg.getType() == null) {
                logger.warning("Received invalid proxy message");
                return;
            }

            handleIncomingMessage(proxyMsg);
        } catch (IOException e) {
            logger.warning("Failed to read proxy message: " + e.getMessage());
        }
    }

    /**
     * Handle an incoming message from the proxy.
     */
    private void handleIncomingMessage(ProxyMessage msg) {
        switch (msg.getType()) {
            case AUTH_RESPONSE -> handleAuthResponse(msg);
            case EXECUTE_COMMAND -> handleExecuteCommand(msg);
            case SEND_MESSAGE -> handleSendMessage(msg);
            case BROADCAST_MESSAGE -> handleBroadcastMessage(msg);
            case REQUEST_SERVER_INFO -> reportServerInfo();
            case REQUEST_PLAYER_DATA -> handleRequestPlayerData(msg);
            case REQUEST_LOGS -> handleRequestLogs(msg);
            default -> {
                // Forward to external handler if set
                if (messageHandler != null) {
                    messageHandler.accept(msg);
                }
            }
        }
    }

    private void handleAuthResponse(ProxyMessage msg) {
        JsonObject data = msg.getData();
        if (data == null) return;

        boolean success = data.has("success") && data.get("success").getAsBoolean();
        String message = data.has("message") ? data.get("message").getAsString() : "";

        if (success) {
            authenticated = true;
            logger.info("代理端认证成功: " + message);
            // Send initial server info after authentication
            reportServerInfo();
        } else {
            authenticated = false;
            logger.severe("代理端认证失败: " + message);
        }
    }

    private void handleExecuteCommand(ProxyMessage msg) {
        JsonObject data = msg.getData();
        if (data == null) return;

        String command = data.has("command") ? data.get("command").getAsString() : null;
        String executor = data.has("executor") ? data.get("executor").getAsString() : "CONSOLE";
        String playerUuid = data.has("playerUuid") ? data.get("playerUuid").getAsString() : null;

        if (command == null || command.isEmpty()) return;

        // Execute on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            long startTime = System.currentTimeMillis();
            boolean success;

            try {
                if ("PLAYER".equalsIgnoreCase(executor) && playerUuid != null) {
                    Player player = Bukkit.getPlayer(UUID.fromString(playerUuid));
                    if (player != null) {
                        success = Bukkit.dispatchCommand(player, command);
                    } else {
                        reportCommandResult(msg.getId(), false, command,
                                "Player not online: " + playerUuid, 0, null);
                        return;
                    }
                } else {
                    success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                }
            } catch (Exception e) {
                reportCommandResult(msg.getId(), false, command,
                        "Exception: " + e.getMessage(), System.currentTimeMillis() - startTime, null);
                return;
            }

            long executionTime = System.currentTimeMillis() - startTime;
            reportCommandResult(msg.getId(), success, command,
                    success ? "Command executed" : "Command execution failed",
                    executionTime, null);
        });
    }

    private void handleSendMessage(ProxyMessage msg) {
        JsonObject data = msg.getData();
        if (data == null) return;

        String playerUuid = data.has("playerUuid") ? data.get("playerUuid").getAsString() : null;
        String playerName = data.has("playerName") ? data.get("playerName").getAsString() : null;
        String message = data.has("message") ? data.get("message").getAsString() : null;

        if (message == null) return;

        Player target = null;
        if (playerUuid != null) {
            try {
                target = Bukkit.getPlayer(UUID.fromString(playerUuid));
            } catch (Exception ignored) {
            }
        }
        if (target == null && playerName != null) {
            target = Bukkit.getPlayerExact(playerName);
        }

        if (target != null) {
            target.sendMessage(message);
        }
    }

    private void handleBroadcastMessage(ProxyMessage msg) {
        JsonObject data = msg.getData();
        if (data == null) return;

        String message = data.has("message") ? data.get("message").getAsString() : null;
        if (message != null) {
            platformAdapter.broadcastMessage(message);
        }
    }

    private void handleRequestPlayerData(ProxyMessage msg) {
        JsonObject data = msg.getData();
        String uuid = data != null && data.has("playerUuid") ? data.get("playerUuid").getAsString() : null;

        if (uuid != null) {
            // Report specific player
            platformAdapter.getPlayer(UUID.fromString(uuid))
                    .ifPresent(this::reportPlayerData);
        } else {
            // Report all players
            for (CommonPlayer player : platformAdapter.getOnlinePlayers()) {
                reportPlayerData(player);
            }
        }
    }

    private void handleRequestLogs(ProxyMessage msg) {
        JsonObject data = msg.getData();
        int lines = data != null && data.has("lines") ? data.get("lines").getAsInt() : 100;

        List<String> logs = platformAdapter.getRecentLogs(lines);

        JsonObject responseData = new JsonObject();
        JsonArray logArray = new JsonArray();
        logs.forEach(logArray::add);
        responseData.add("logs", logArray);
        responseData.addProperty("total", logs.size());

        ProxyMessage response = ProxyMessage.builder()
                .type(ProxyMessageType.LOG_REPORT)
                .replyTo(msg.getId())
                .serverName(platformAdapter.getServerName())
                .data(responseData)
                .build();

        sendMessage(response);
    }

    /**
     * Send a ProxyMessage to the proxy via Plugin Messaging Channel.
     */
    public void sendMessage(ProxyMessage msg) {
        // Need at least one online player to send plugin messages
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        if (players.isEmpty()) {
            return; // Cannot send plugin messages without online players
        }

        try {
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteOut);
            out.writeUTF(msg.toJson());

            byte[] data = byteOut.toByteArray();
            if (data.length > ProxyChannel.MAX_PAYLOAD_SIZE) {
                logger.warning("Proxy message too large (" + data.length + " bytes), dropping");
                return;
            }

            Player sender = players.iterator().next();
            sender.sendPluginMessage(plugin, ProxyChannel.CHANNEL_ID, data);
        } catch (IOException e) {
            logger.warning("Failed to send proxy message: " + e.getMessage());
        }
    }

    /**
     * Whether the client is authenticated with the proxy.
     */
    public boolean isAuthenticated() {
        return authenticated;
    }
}
