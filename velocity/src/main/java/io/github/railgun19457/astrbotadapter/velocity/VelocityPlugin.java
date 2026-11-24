package io.github.railgun19457.astrbotadapter.velocity;

import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.util.Favicon;
import io.github.railgun19457.astrbotadapter.common.EndpointHost;
import io.github.railgun19457.astrbotadapter.protocol.SecureChannel;
import io.github.railgun19457.astrbotadapter.transport.AdapterServer.RequestFailure;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.Function;

import static io.github.railgun19457.astrbotadapter.common.EndpointHost.*;

public final class VelocityPlugin {
    private static final Logger LOGGER = Logger.getLogger("AstrBotAdapter.Velocity");
    private static final PlainTextComponentSerializer TEXT = PlainTextComponentSerializer.plainText();
    private final ProxyServer proxy;
    private final Path directory;
    private final Map<UUID, String> lastServer = new ConcurrentHashMap<>();
    private final ExecutorService nativeCalls = Executors.newVirtualThreadPerTaskExecutor();
    private volatile EndpointHost host;

    @Inject
    public VelocityPlugin(ProxyServer proxy, @DataDirectory Path directory) {
        this.proxy = proxy;
        this.directory = directory;
    }

    @Subscribe
    public void start(ProxyInitializeEvent event) {
        try {
            host = new EndpointHost(directory, "velocity", "network", 8765, LOGGER, this::request,
                name -> proxy.getServer(name).filter(server -> serverId(server).equals(name)).isPresent());
            host.start();
            var commands = proxy.getCommandManager();
            commands.register(commands.metaBuilder("astrbot").plugin(this).build(), (SimpleCommand) this::onCommand);
        } catch (Exception failure) {
            LOGGER.log(Level.SEVERE, "Cannot initialize AstrBotAdapter", failure);
        }
    }

    @Subscribe
    public void stop(ProxyShutdownEvent event) {
        EndpointHost active = host;
        host = null;
        if (active != null) {
            active.close();
        }
        nativeCalls.shutdownNow();
    }

    private CompletionStage<JsonObject> request(String client, String id, String operation, JsonObject data) {
        try {
            String target = target(data);
            if (!target.isEmpty()) {
                var selected = servers(data).getFirst();
                target = serverId(selected);
                if (operation.equals("server.status")) {
                    return backendStatus(selected, client, id, data);
                }
                var nativeBackend = nativeBackend(selected);
                if (operation.equals("command.run") && host.settings().property("backends." + target + ".command_source", "adapter").equals("rcon")) {
                    if (!nativeBackend.rconEnabled()) {
                        throw new RequestFailure("rcon_config", "Configure this backend's RCON port");
                    }
                    return host.runCommand(client, id, data, nativeBackend.rconCommands(),
                        command -> CompletableFuture.supplyAsync(() -> nativeBackend.rcon(command), nativeCalls));
                }
                if (List.of("player.list", "player.detail", "command.run", "log.query").contains(operation)
                        && host.backendReady(target)) {
                    return backendRequest(target, client, id, operation, data);
                }
                if (operation.equals("command.run") || operation.equals("log.query")) {
                    throw new RequestFailure("backend_offline", "No backend adapter is connected for this operation");
                }
                if (operation.equals("player.list") && (nativeBackend.queryEnabled() || nativeBackend.managementEnabled())) {
                    return nativePlayers(nativeBackend, data);
                }
            }
            return switch (operation) {
                case "server.list" -> CompletableFuture.completedFuture(object(Map.of(
                    "servers", proxy.getAllServers().stream().map(this::serverId).sorted().limit(250).toList()
                )));
                case "server.status" -> status(client, id, data);
                case "player.list" -> CompletableFuture.completedFuture(list(data));
                case "player.detail" -> playerDetail(client, id, data);
                case "chat.send" -> CompletableFuture.completedFuture(deliver(data));
                case "log.query" -> host.logs(data);
                case "command.run" -> host.runCommand(client, id, data, this::command);
                default -> throw new RequestFailure("unsupported", "Unsupported proxy operation");
            };
        } catch (IllegalArgumentException failure) {
            return CompletableFuture.failedFuture(new RequestFailure("bad_request", failure.getMessage()));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private CompletionStage<JsonObject> backendRequest(String target, String client, String id, String operation, JsonObject request) {
        var data = request.deepCopy();
        data.remove("target");
        return host.backendRequest(target, client, id, operation, data).thenApply(reply -> {
            if (operation.equals("player.detail")) {
                reply.addProperty("server", target);
            }
            return reply;
        });
    }

    private CompletableFuture<JsonObject> backendStatus(RegisteredServer server, String client, String id, JsonObject request) {
        String target = serverId(server);
        if (!host.backendReady(target)) {
            return nativeStatus(server, option(request, "include_assets")).thenApply(fallback -> {
                if (host.backendPaired(target)) {
                    fallback.addProperty("adapter_connected", false);
                }
                return fallback;
            });
        }
        return backendRequest(target, client, id, "server.status", request).handle((result, error) -> {
            if (error == null) {
                result.addProperty("reachable", true);
                result.addProperty("source", "adapter");
                result.addProperty("adapter_connected", true);
                result.addProperty("proxy_online", server.getPlayersConnected().size());
                return CompletableFuture.completedFuture(result);
            }
            return ping(server, option(request, "include_assets")).thenApply(fallback -> {
                fallback.addProperty("adapter_error", "backend_unavailable");
                fallback.addProperty("adapter_connected", host.backendReady(target));
                return fallback;
            });
        }).thenCompose(Function.identity()).toCompletableFuture();
    }

    private NativeBackend nativeBackend(RegisteredServer server) {
        return new NativeBackend(host.settings(), serverId(server), server.getServerInfo().getAddress().getHostString());
    }

    private CompletableFuture<JsonObject> nativeStatus(RegisteredServer server, boolean assets) {
        var nativeBackend = nativeBackend(server);
        var ping = ping(server, assets);
        if (!nativeBackend.queryEnabled() && !nativeBackend.managementEnabled()) {
            return ping;
        }
        CompletableFuture<JsonObject> query = nativeBackend.queryEnabled() ? nativeCall(nativeBackend::query) : CompletableFuture.completedFuture(null);
        CompletableFuture<JsonObject> management = nativeBackend.managementEnabled()
            ? nativeCall(() -> nativeBackend.management("minecraft:server/status").getAsJsonObject())
            : CompletableFuture.completedFuture(null);
        return ping.thenCombine(query, (status, queried) -> {
            if (queried != null) {
                for (String field : List.of("motd", "minecraft", "capacity", "reported_online")) {
                    if (queried.has(field)) {
                        status.add(field, queried.get(field));
                    }
                }
                status.addProperty("reachable", true);
                status.addProperty("source", "query");
            }
            return status;
        }).thenCombine(management, (status, managed) -> {
            if (managed != null && managed.has("started")) {
                status.addProperty("reachable", true);
                status.addProperty("started", managed.get("started").getAsBoolean());
                status.addProperty("source", "management");
                if (managed.has("players")) {
                    status.addProperty("reported_online", managed.getAsJsonArray("players").size());
                }
                if (managed.has("version") && managed.getAsJsonObject("version").has("name")) {
                    status.addProperty("minecraft", limit(managed.getAsJsonObject("version").get("name").getAsString(), 128));
                }
            }
            return status;
        });
    }

    private CompletionStage<JsonObject> nativePlayers(NativeBackend backend, JsonObject request) {
        var proxyList = list(request);
        CompletableFuture<JsonObject> query = backend.queryEnabled() ? nativeCall(backend::query) : CompletableFuture.completedFuture(null);
        var management = backend.managementEnabled() ? nativeCall(() -> {
            var result = backend.management("minecraft:players").getAsJsonArray();
            List<JsonObject> players = new ArrayList<>();
            for (var player : result) {
                var profile = player.getAsJsonObject();
                if (players.size() == 250) {
                    break;
                }
                var entry = object(Map.of("name", limit(profile.get("name").getAsString(), 32), "server", backend.id()));
                if (profile.has("id")) {
                    entry.add("uuid", profile.get("id"));
                }
                players.add(entry);
            }
            return object(Map.of("source", "management", "players", players, "total", result.size()));
        }) : CompletableFuture.<JsonObject>completedFuture(null);
        return query.thenCombine(management, (queried, managed) -> {
            var roster = managed != null ? managed : queried != null ? queried.getAsJsonObject("players") : null;
            if (roster == null) {
                return proxyList;
            }
            var localPlayers = new java.util.HashMap<String, JsonObject>();
            proxyList.getAsJsonArray("players").forEach(item -> {
                var profile = item.getAsJsonObject();
                localPlayers.put(profile.get("name").getAsString(), profile);
            });
            for (var player : roster.getAsJsonArray("players")) {
                var profile = player.getAsJsonObject();
                var local = localPlayers.get(profile.get("name").getAsString());
                if (local != null) {
                    local.entrySet().forEach(field -> profile.add(field.getKey(), field.getValue()));
                }
            }
            return roster;
        });
    }

    private <T> CompletableFuture<T> nativeCall(java.util.concurrent.Callable<T> operation) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return operation.call();
            } catch (Exception failure) {
                LOGGER.fine("Backend native query was unavailable: " + failure.getClass().getSimpleName());
                return null;
            }
        }, nativeCalls);
    }

    private String serverId(RegisteredServer server) {
        return server.getServerInfo().getName();
    }

    private String currentServer(Player player) {
        return player.getCurrentServer().map(connection -> serverId(connection.getServer())).orElse("");
    }

    private List<RegisteredServer> servers(JsonObject data) {
        var selection = target(data);
        if (!selection.isEmpty()) {
            return List.of(proxy.getServer(selection).orElseThrow(
                () -> new RequestFailure("unknown_target", "Unknown backend server")));
        }
        return proxy.getAllServers().stream().sorted(Comparator.comparing(this::serverId)).limit(32).toList();
    }

    private Collection<Player> players(JsonObject data) {
        if (target(data).isEmpty()) {
            return proxy.getAllPlayers();
        }
        return servers(data).stream().flatMap(server -> server.getPlayersConnected().stream()).distinct().toList();
    }

    private CompletionStage<JsonObject> status(String client, String id, JsonObject data) {
        boolean assets = option(data, "include_assets");
        var selected = servers(data);
        Runtime runtime = Runtime.getRuntime();
        long memory = runtime.totalMemory() - runtime.freeMemory();
        JsonObject summary = object(Map.of(
            "server_id", host.id, "name", host.settings().name(), "platform", "Velocity",
            "minecraft", proxy.getVersion().getVersion(), "online", proxy.getPlayerCount(),
            "capacity", proxy.getConfiguration().getShowMaxPlayers(),
            "memory_mb", memory / 1_048_576, "memory_percent", 100.0 * memory / runtime.maxMemory(),
            "uptime_seconds", host.uptime()
        ));
        if (assets) {
            proxy.getConfiguration().getFavicon().ifPresent(icon -> appendIcon(summary, icon));
        }
        List<CompletableFuture<JsonObject>> pings = selected.stream().map(server -> backendStatus(server, client, id, new JsonObject())).toList();
        return CompletableFuture.allOf(pings.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
            var array = new com.google.gson.JsonArray();
            pings.forEach(result -> array.add(result.join()));
            summary.add("servers", array);
            return summary;
        });
    }

    private CompletableFuture<JsonObject> ping(RegisteredServer server, boolean assets) {
        return server.ping().orTimeout(2, TimeUnit.SECONDS).handle((status, failure) -> {
            String id = serverId(server);
            var info = object(Map.of("server_id", id, "name", id, "platform", "Velocity",
                "online", server.getPlayersConnected().size(), "reachable", failure == null, "source", "ping"));
            if (failure == null) {
                info.addProperty("minecraft", limit(status.getVersion().getName(), 128));
                info.addProperty("motd", limit(TEXT.serialize(status.getDescriptionComponent()), 200));
                status.getPlayers().ifPresent(players -> {
                    info.addProperty("capacity", players.getMax());
                    info.addProperty("reported_online", players.getOnline());
                });
                if (assets) {
                    status.getFavicon().ifPresent(icon -> appendIcon(info, icon));
                }
            }
            return info;
        });
    }

    private static void appendIcon(JsonObject data, Favicon icon) {
        String encoded = icon.getBase64Url();
        if (encoded.length() <= 32_768) {
            data.addProperty("icon", encoded);
        }
    }

    private JsonObject list(JsonObject data) {
        var online = List.copyOf(players(data));
        boolean assets = option(data, "include_assets");
        boolean ping = option(data, "include_ping");
        return object(Map.of("total", online.size(), "players", online.stream().limit(assets || ping ? 30 : 250)
            .map(player -> playerData(player, assets, ping)).toList()));
    }

    private JsonObject detail(JsonObject data) {
        String query = SecureChannel.text(data, "player", 64);
        Player player = players(data).stream().filter(candidate -> candidate.getUsername().equalsIgnoreCase(query)
                || candidate.getUniqueId().toString().equalsIgnoreCase(query)).findFirst()
            .orElseThrow(() -> new RequestFailure("player_offline", "Player is not online on the selected server"));
        return playerData(player, option(data, "include_assets"), true);
    }

    private CompletionStage<JsonObject> playerDetail(String client, String id, JsonObject data) {
        JsonObject identity = detail(data);
        String target = identity.get("server").getAsString();
        if (host.backendReady(target)) {
            return backendRequest(target, client, id, "player.detail", data);
        }
        return CompletableFuture.completedFuture(identity);
    }

    private JsonObject playerData(Player player, boolean assets, boolean ping) {
        var result = object(Map.of("name", player.getUsername(), "uuid", player.getUniqueId().toString(), "server", currentServer(player)));
        if (ping && player.getPing() >= 0) {
            result.addProperty("ping", player.getPing());
        }
        if (assets) {
            player.getGameProfileProperties().stream().filter(property -> property.getName().equals("textures"))
                .findFirst().ifPresent(property -> profileSkin(result, property.getValue()));
        }
        return result;
    }

    private JsonObject deliver(JsonObject data) {
        String destination = target(data);
        if (destination.isEmpty() && !"bridge".equals(SecureChannel.text(data, "kind", 16))) {
            throw new RequestFailure("target_required", "Select a backend server for chat delivery");
        }
        var recipients = players(data);
        Component message = Component.text(host.message(data));
        String id = SecureChannel.text(data, "player", 36);
        if (!id.isEmpty()) {
            UUID uuid = UUID.fromString(id);
            var player = recipients.stream().filter(value -> value.getUniqueId().equals(uuid)
                && currentServer(value).equals(destination)).findFirst()
                .orElseThrow(() -> new RequestFailure("player_offline", "Player is no longer on this backend"));
            player.sendMessage(message);
            return object(Map.of("delivered", 1));
        }
        List<Player> current = recipients.stream().filter(player -> {
            String server = currentServer(player);
            return destination.isEmpty() ? !server.isEmpty() : destination.equals(server);
        }).toList();
        current.forEach(player -> player.sendMessage(message));
        return object(Map.of("delivered", current.size()));
    }

    private CompletionStage<JsonObject> command(String value) {
        List<String> lines = new ArrayList<>();
        CommandSource source = new CommandSource() {
            @Override public Tristate getPermissionValue(String permission) {
                return proxy.getConsoleCommandSource().getPermissionValue(permission);
            }
            @Override public void sendMessage(Component message) {
                synchronized (lines) {
                    if (lines.size() < 16) {
                        lines.add(limit(TEXT.serialize(message), 320));
                    }
                }
            }
        };
        return proxy.getCommandManager().executeAsync(source, value).thenApply(accepted -> {
            synchronized (lines) {
                return object(Map.of("accepted", accepted, "output", List.copyOf(lines)));
            }
        });
    }

    @Subscribe(order = PostOrder.LAST)
    public void chat(PlayerChatEvent event) {
        var active = host;
        if (active == null) {
            return;
        }
        var settings = active.settings();
        if (settings.chat() && event.getResult().isAllowed() && event.getMessage().startsWith(settings.prefix())) {
            var data = playerData(event.getPlayer(), false, false);
            data.addProperty("text", limit(event.getMessage().substring(settings.prefix().length()).strip(), 1500));
            active.publish("chat.message", data);
        }
    }

    @Subscribe
    public void joinOrSwitch(ServerPostConnectEvent event) {
        var active = host;
        if (active != null && active.settings().events()) {
            var data = playerData(event.getPlayer(), false, false);
            lastServer.put(event.getPlayer().getUniqueId(), data.get("server").getAsString());
            var previous = event.getPreviousServer();
            if (previous != null) {
                data.addProperty("previous", serverId(previous));
            }
            active.publish(previous == null ? "player.join" : "player.switch", data);
        }
    }

    @Subscribe
    public void leave(DisconnectEvent event) {
        var active = host;
        String server = lastServer.remove(event.getPlayer().getUniqueId());
        if (active != null && active.settings().events() && server != null) {
            var data = playerData(event.getPlayer(), false, false);
            data.addProperty("server", server);
            active.publish("player.leave", data);
        }
    }

    private void onCommand(SimpleCommand.Invocation invocation) {
        var active = host;
        var source = invocation.source();
        var args = invocation.arguments();
        if (active == null) {
            source.sendMessage(Component.text("AstrBotAdapter is offline"));
            return;
        }
        if (source instanceof Player player && args.length > 0) {
            if (args[0].equals("ai") || args[0].equals("ai-public")) {
                boolean publicly = args[0].equals("ai-public");
                String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).strip();
                if (!active.settings().ai() || text.isEmpty() || currentServer(player).isEmpty()
                    || publicly && !player.hasPermission("astrbot.ai.public")) {
                    source.sendMessage(Component.text("Usage or permission: /astrbot ai <message>"));
                } else if (active.connections() == 0) {
                    source.sendMessage(Component.text("AstrBot is not connected"));
                } else {
                    var data = playerData(player, false, false);
                    data.addProperty("text", limit(text, 1500));
                    data.addProperty("public", publicly);
                    active.publish("ai.request", data);
                }
                return;
            }
            if (args[0].equals("bind")) {
                if (args.length == 2 && args[1].matches("[A-Z2-7]{8}") && active.connections() > 0) {
                    var data = playerData(player, false, false);
                    data.addProperty("code", args[1]);
                    active.publish("player.bind", data);
                    source.sendMessage(Component.text("Binding submitted to AstrBot"));
                } else {
                    source.sendMessage(Component.text("Usage: /astrbot bind <code> (AstrBot must be connected)"));
                }
                return;
            }
        }
        boolean admin = !(source instanceof Player) || source.hasPermission("astrbot.admin");
        if (admin && args.length >= 1 && args[0].equals("config")) {
            boolean set = args.length >= 5 && args[1].equals("set");
            boolean reset = args.length == 4 && args[1].equals("reset");
            if ((!set && !reset) || proxy.getServer(args[2]).isEmpty()) {
                source.sendMessage(Component.text("/astrbot config set <backend> <key> <value> | config reset <backend> <key>"));
                return;
            }
            var settings = object(Map.of("key", args[3], "reset", reset,
                "value", set ? String.join(" ", Arrays.copyOfRange(args, 4, args.length)) : ""));
            String target = serverId(proxy.getServer(args[2]).orElseThrow());
            active.backendRequest(target, "velocity-admin", SecureChannel.randomToken(16), "config.update", settings)
                .whenComplete((reply, error) -> {
                    var cause = error != null && error.getCause() != null ? error.getCause() : error;
                    source.sendMessage(Component.text(cause == null ? "Backend setting updated: " + args[3]
                        : cause instanceof RequestFailure failure ? failure.getMessage() : "Could not update the backend setting"));
                });
            return;
        }
        source.sendMessage(Component.text(admin ? active.admin(args) : "/astrbot ai <message> | /astrbot bind <code>"));
    }
}
