package io.github.railgun19457.astrbotadapter.paper;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.railgun19457.astrbotadapter.common.EndpointHost;
import io.github.railgun19457.astrbotadapter.protocol.SecureChannel;
import io.github.railgun19457.astrbotadapter.transport.AdapterServer.RequestFailure;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.regex.Pattern;

public final class PaperPlugin extends JavaPlugin implements Listener {
    private static final Gson JSON = new Gson();
    private static final PlainTextComponentSerializer TEXT = PlainTextComponentSerializer.plainText();
    private static final Pattern SKIN_PATH = Pattern.compile("/texture/([0-9a-f]{32,64})");
    private volatile EndpointHost host;
    private FoliaMetrics foliaMetrics;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            if (Bukkit.getName().equalsIgnoreCase("Folia")) {
                foliaMetrics = FoliaMetrics.open(Bukkit.getServer().getClass().getClassLoader(), getLogger());
            }
            host = new EndpointHost(getDataFolder().toPath(), "standalone", "survival", 8765, getLogger(),
                this::request, null, this::readSettings);
            host.start();
            getServer().getPluginManager().registerEvents(this, this);
        } catch (IOException | GeneralSecurityException | RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Cannot initialize AstrBotAdapter", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private Properties readSettings() throws IOException {
        var yaml = new YamlConfiguration();
        try {
            yaml.load(getDataFolder().toPath().resolve("config.yml").toFile());
        } catch (InvalidConfigurationException failure) {
            throw new IOException("Invalid config.yml", failure);
        }
        var properties = new Properties();
        yaml.getValues(true).forEach((key, value) -> {
            if (value instanceof String || value instanceof Boolean || value instanceof Number) {
                properties.setProperty(key, value.toString());
            }
        });
        var aliases = Map.of("transport.host", "transport.listen.host", "transport.port", "transport.listen.port",
            "transport.path", "transport.listen.astrbot_path", "chat.prefix", "chat.game_to_group_prefix",
            "commands.enabled", "commands.remote.enabled");
        aliases.forEach((target, source) -> {
            if (properties.containsKey(source)) {
                properties.setProperty(target, properties.getProperty(source));
            }
        });
        if (yaml.isList("commands.remote.allow")) {
            properties.setProperty("commands.allow", String.join(",", yaml.getStringList("commands.remote.allow")));
        }
        return properties;
    }

    @Override
    public void onDisable() {
        var active = host;
        host = null;
        if (active != null) {
            active.close();
        }
    }

    private CompletableFuture<JsonObject> request(String client, String id, String operation, JsonObject data) {
        CompletableFuture<JsonObject> result = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().execute(this, () -> {
            try {
                var active = host;
                if (active == null) {
                    throw new RequestFailure("offline", "The server is shutting down");
                }
                var response = switch (operation) {
                    case "server.status" -> CompletableFuture.completedFuture(status(active, requestOption(data, "include_assets")));
                    case "player.list" -> playerList(requestOption(data, "include_assets"), requestOption(data, "include_ping"));
                    case "player.detail" -> playerDetail(active, data);
                    case "log.query" -> active.logs(data);
                    case "config.update" -> CompletableFuture.completedFuture(active.updateConfig(data));
                    case "chat.send" -> deliver(active, data);
                    case "command.run" -> active.runCommand(client, id, data,
                        command -> CompletableFuture.completedFuture(execute(command)));
                    default -> throw new RequestFailure("unsupported", "This server does not support the requested operation");
                };
                response.whenComplete((value, error) -> {
                    if (error == null) {
                        result.complete(value);
                    } else {
                        result.completeExceptionally(error);
                    }
                });
            } catch (IllegalArgumentException exception) {
                result.completeExceptionally(new RequestFailure("bad_request", exception.getMessage()));
            } catch (RuntimeException exception) {
                result.completeExceptionally(exception);
            }
        });
        return result;
    }

    private JsonObject status(EndpointHost active, boolean includeAssets) {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        JsonObject response = object(Map.of(
            "server_id", active.id,
            "name", active.settings().name(),
            "platform", Bukkit.getName(),
            "minecraft", Bukkit.getMinecraftVersion(),
            "online", Bukkit.getOnlinePlayers().size(),
            "capacity", Bukkit.getMaxPlayers(),
            "memory_mb", usedMemory / 1_048_576,
            "memory_percent", 100.0 * usedMemory / runtime.maxMemory(),
            "uptime_seconds", active.uptime()
        ));
        if (!"Folia".equalsIgnoreCase(Bukkit.getName())) {
            response.addProperty("tps", Bukkit.getTPS()[0]);
            response.addProperty("mspt", Bukkit.getAverageTickTime());
        } else if (foliaMetrics != null) {
            foliaMetrics.addTo(response);
        }
        if (includeAssets) {
            var icon = Bukkit.getServerIcon();
            String image = icon == null ? null : icon.getData();
            if (image != null && image.length() <= 32_768) {
                response.addProperty("icon", image);
            }
        }
        return response;
    }

    private CompletableFuture<JsonObject> playerList(boolean includeAssets, boolean includePing) {
        var players = List.copyOf(Bukkit.getOnlinePlayers());
        if (!includeAssets && !includePing) {
            return CompletableFuture.completedFuture(object(Map.of("total", players.size(), "players",
                players.stream().limit(250).map(PaperPlugin::playerData).toList())));
        }
        List<CompletableFuture<JsonObject>> snapshots = players.stream().limit(30).map(player -> {
            JsonObject identity = playerData(player);
            return onPlayer(player, () -> {
                if (includePing) {
                    identity.addProperty("ping", player.getPing());
                }
                return includeAssets ? withSkin(player, identity) : identity;
            }, identity);
        }).toList();
        return CompletableFuture.allOf(snapshots.toArray(CompletableFuture[]::new)).thenApply(ignored ->
            object(Map.of("total", players.size(), "players", snapshots.stream().map(CompletableFuture::join).toList())));
    }

    private CompletableFuture<JsonObject> playerDetail(EndpointHost active, JsonObject data) {
        String query = SecureChannel.text(data, "player", 64);
        Player player = Bukkit.getOnlinePlayers().stream()
            .filter(value -> value.getName().equalsIgnoreCase(query) || value.getUniqueId().toString().equalsIgnoreCase(query))
            .findFirst().orElse(null);
        boolean includeAssets = requestOption(data, "include_assets");
        boolean onlineRequired = requestOption(data, "online_only");
        if (player == null) {
            return active.offlinePlayer(query, includeAssets, onlineRequired);
        }
        return onPlayer(player, () -> playerSnapshot(player, includeAssets), null).thenCompose(snapshot ->
            snapshot == null ? active.offlinePlayer(query, includeAssets, onlineRequired) : CompletableFuture.completedFuture(snapshot));
    }

    private JsonObject playerSnapshot(Player player, boolean includeAssets) {
        Location location = player.getLocation();
        JsonObject response = playerData(player);
        response.addProperty("ping", player.getPing());
        response.addProperty("world", player.getWorld().getName());
        response.addProperty("game_mode", player.getGameMode().name());
        response.addProperty("health", player.getHealth());
        response.add("position", JSON.toJsonTree(List.of(location.getX(), location.getY(), location.getZ())));
        return includeAssets ? withSkin(player, response) : response;
    }

    private static boolean requestOption(JsonObject data, String key) {
        var option = data.get(key);
        return option != null && option.isJsonPrimitive() && option.getAsJsonPrimitive().isBoolean()
            && option.getAsBoolean();
    }

    private static JsonObject withSkin(Player player, JsonObject identity) {
        URL skin = player.getPlayerProfile().getTextures().getSkin();
        if (skin != null && "textures.minecraft.net".equalsIgnoreCase(skin.getHost())) {
            var path = SKIN_PATH.matcher(skin.getPath());
            if (path.matches()) {
                identity.addProperty("skin", path.group(1));
            }
        }
        return identity;
    }

    private CompletableFuture<JsonObject> deliver(EndpointHost active, JsonObject data) {
        String recipient = SecureChannel.text(data, "player", 36);
        Component component = Component.text(active.message(data));
        if (recipient.isEmpty()) {
            List<CompletableFuture<Boolean>> sends = List.copyOf(Bukkit.getOnlinePlayers()).stream()
                .map(player -> sendToPlayer(player, component)).toList();
            return CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new)).thenApply(ignored ->
                object(Map.of("delivered", sends.stream().filter(CompletableFuture::join).count())));
        }
        Player player = Bukkit.getPlayer(UUID.fromString(recipient));
        if (player == null) {
            throw new RequestFailure("player_offline", "Player is no longer online");
        }
        return sendToPlayer(player, component).thenApply(sent -> {
            if (!sent) {
                throw new RequestFailure("player_offline", "Player is no longer online");
            }
            return object(Map.of("delivered", 1));
        });
    }

    private CompletableFuture<Boolean> sendToPlayer(Player player, Component message) {
        return onPlayer(player, () -> {
            player.sendMessage(message);
            return true;
        }, false);
    }

    private <T> CompletableFuture<T> onPlayer(Player player, Supplier<T> action, T offline) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Runnable retired = () -> result.complete(offline);
        boolean scheduled = player.getScheduler().execute(this, () -> {
            try {
                result.complete(player.isOnline() ? action.get() : offline);
            } catch (RuntimeException exception) {
                result.completeExceptionally(exception);
            }
        }, retired, 1);
        if (!scheduled) {
            retired.run();
        }
        return result;
    }

    private JsonObject execute(String command) {
        List<String> output = new ArrayList<>();
        CommandSender sender = Bukkit.getServer().createCommandSender(message -> {
            if (output.size() < 16) {
                output.add(limit(TEXT.serialize(message), 320));
            }
        });
        boolean accepted = Bukkit.dispatchCommand(sender, command);
        return object(Map.of("accepted", accepted, "output", output));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void chat(AsyncChatEvent event) {
        var active = host;
        if (active == null || active.backendMode()) {
            return;
        }
        var settings = active.settings();
        String content = TEXT.serialize(event.message());
        if (settings.chat() && content.startsWith(settings.prefix())) {
            JsonObject data = playerData(event.getPlayer());
            data.addProperty("text", limit(content.substring(settings.prefix().length()).strip(), 1500));
            active.publish("chat.message", data);
        }
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        var active = host;
        if (active != null && active.settings().events()) {
            active.publish("player.join", playerData(event.getPlayer()));
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        var active = host;
        if (active != null) {
            if (active.keepsOfflinePlayers()) {
                active.rememberPlayer(playerSnapshot(event.getPlayer(), true));
            }
            if (active.settings().events()) {
                active.publish("player.leave", playerData(event.getPlayer()));
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var active = host;
        if (active == null) {
            sender.sendMessage("AstrBotAdapter is offline");
            return true;
        }
        if (!active.backendMode() && args.length >= 1 && sender instanceof Player player) {
            if (args[0].equals("ai") || args[0].equals("ai-public")) {
                boolean publicly = args[0].equals("ai-public");
                String question = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).strip();
                if (question.isEmpty() || !active.settings().ai() || !player.hasPermission(publicly ? "astrbot.ai.public" : "astrbot.ai")) {
                    sender.sendMessage("Usage or permission: /astrbot ai <message>");
                } else if (active.connections() == 0) {
                    sender.sendMessage("AstrBot is not connected");
                } else {
                    JsonObject data = playerData(player);
                    data.addProperty("text", limit(question, 1500));
                    data.addProperty("public", publicly);
                    active.publish("ai.request", data);
                }
                return true;
            }
            if (args[0].equals("bind")) {
                if (args.length == 2 && args[1].matches("[A-Z2-7]{8}") && active.connections() > 0) {
                    JsonObject data = playerData(player);
                    data.addProperty("code", args[1]);
                    active.publish("player.bind", data);
                    sender.sendMessage("Binding submitted to AstrBot");
                } else {
                    sender.sendMessage("Usage: /astrbot bind <code> (AstrBot must be connected)");
                }
                return true;
            }
        }
        if (!sender.hasPermission("astrbot.admin")) {
            sender.sendMessage("/astrbot ai <message>, /astrbot bind <code>");
            return true;
        }
        sender.sendMessage(active.admin(args));
        return true;
    }

    private static JsonObject playerData(Player player) {
        return object(Map.of("name", player.getName(), "uuid", player.getUniqueId().toString()));
    }

    private static JsonObject object(Map<String, ?> values) {
        return JSON.toJsonTree(values).getAsJsonObject();
    }

    private static String limit(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length);
    }
}
