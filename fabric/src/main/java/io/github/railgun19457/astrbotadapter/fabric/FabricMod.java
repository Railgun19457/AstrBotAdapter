package io.github.railgun19457.astrbotadapter.fabric;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.railgun19457.astrbotadapter.common.EndpointHost;
import io.github.railgun19457.astrbotadapter.protocol.SecureChannel;
import io.github.railgun19457.astrbotadapter.transport.AdapterServer.RequestFailure;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.github.railgun19457.astrbotadapter.common.EndpointHost.*;

public final class FabricMod implements DedicatedServerModInitializer {
    private static final Logger LOGGER = Logger.getLogger("AstrBotAdapter.Fabric");
    private volatile EndpointHost host;
    private long sampledAt;
    private int sampledAtTick;
    private Double ticksPerSecond;

    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::start);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.nanoTime();
            long elapsed = now - sampledAt;
            if (host != null && elapsed >= 5_000_000_000L) {
                ticksPerSecond = (server.getTickCount() - sampledAtTick) * 1_000_000_000.0 / elapsed;
                sampledAt = now;
                sampledAtTick = server.getTickCount();
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            EndpointHost active = host;
            host = null;
            if (active != null) {
                active.close();
            }
        });
        ServerPlayConnectionEvents.JOIN.register((connection, sender, server) -> presence("player.join", connection.player));
        ServerPlayConnectionEvents.DISCONNECT.register((connection, server) -> presence("player.leave", connection.player));
        ServerMessageEvents.CHAT_MESSAGE.register((message, player, chatType) -> {
            var active = host;
            if (active == null || active.backendMode()) {
                return;
            }
            var settings = active.settings();
            String text = message.signedContent();
            if (settings.chat() && text.startsWith(settings.prefix())) {
                var data = playerData(player, false, false);
                data.addProperty("text", limit(text.substring(settings.prefix().length()).strip(), 1500));
                active.publish("chat.message", data);
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
            Commands.literal("astrbot")
                .executes(context -> onCommand(context.getSource(), ""))
                .then(Commands.argument("arguments", StringArgumentType.greedyString())
                    .executes(context -> onCommand(context.getSource(), StringArgumentType.getString(context, "arguments"))))
        ));
    }

    private void start(MinecraftServer server) {
        try {
            var directory = FabricLoader.getInstance().getConfigDir().resolve("astrbotadapter");
            sampledAt = System.nanoTime();
            sampledAtTick = server.getTickCount();
            ticksPerSecond = null;
            host = new EndpointHost(directory, "standalone", "fabric", 8765, LOGGER,
                (client, id, operation, data) -> request(server, client, id, operation, data), null);
            host.start();
        } catch (Exception failure) {
            LOGGER.log(Level.SEVERE, "Cannot initialize AstrBotAdapter", failure);
        }
    }

    private CompletionStage<JsonObject> request(MinecraftServer server, String client, String id,
                                                 String operation, JsonObject data) {
        CompletableFuture<JsonObject> result = new CompletableFuture<>();
        server.execute(() -> {
            try {
                var active = host;
                if (active == null) {
                    throw new RequestFailure("offline", "The server is shutting down");
                }
                if (operation.equals("command.run")) {
                    active.runCommand(client, id, data, command -> CompletableFuture.completedFuture(execute(server, command)))
                        .whenComplete((output, failure) -> {
                            if (failure == null) {
                                result.complete(output);
                            } else {
                                result.completeExceptionally(failure);
                            }
                        });
                    return;
                }
                if (operation.equals("player.detail") || operation.equals("log.query")) {
                    var response = operation.equals("player.detail") ? detail(server, active, data) : active.logs(data);
                    response.whenComplete((value, failure) -> {
                        if (failure == null) {
                            result.complete(value);
                        } else {
                            result.completeExceptionally(failure);
                        }
                    });
                    return;
                }
                result.complete(switch (operation) {
                    case "server.status" -> status(server, active, option(data, "include_assets"));
                    case "player.list" -> list(server, data);
                    case "config.update" -> active.updateConfig(data);
                    case "chat.send" -> deliver(server, active, data);
                    default -> throw new RequestFailure("unsupported", "Unsupported server operation");
                });
            } catch (IllegalArgumentException failure) {
                result.completeExceptionally(new RequestFailure("bad_request", failure.getMessage()));
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    private JsonObject status(MinecraftServer server, EndpointHost active, boolean assets) {
        var runtime = Runtime.getRuntime();
        long memory = runtime.totalMemory() - runtime.freeMemory();
        double mspt = server.getAverageTickTimeNanos() / 1_000_000.0;
        var result = object(Map.of(
            "server_id", active.id, "name", active.settings().name(), "platform", "Fabric",
            "minecraft", SharedConstants.getCurrentVersion().name(), "online", server.getPlayerCount(),
            "capacity", server.getPlayerList().getMaxPlayers(),
            "memory_mb", memory / 1_048_576, "memory_percent", 100.0 * memory / runtime.maxMemory(),
            "uptime_seconds", active.uptime()
        ));
        result.addProperty("mspt", mspt);
        if (ticksPerSecond != null) {
            result.addProperty("tps", ticksPerSecond);
        }
        if (assets && server.getStatus() != null) {
            server.getStatus().favicon().ifPresent(icon -> {
                if (icon.iconBytes().length <= 24_000) {
                    result.addProperty("icon", "data:image/png;base64," + Base64.getEncoder().encodeToString(icon.iconBytes()));
                }
            });
        }
        return result;
    }

    private JsonObject list(MinecraftServer server, JsonObject request) {
        boolean assets = option(request, "include_assets");
        boolean ping = option(request, "include_ping");
        var players = server.getPlayerList().getPlayers();
        return object(Map.of("total", players.size(), "players", players.stream().limit(assets || ping ? 30 : 250)
            .map(player -> playerData(player, assets, ping)).toList()));
    }

    private CompletionStage<JsonObject> detail(MinecraftServer server, EndpointHost active, JsonObject request) {
        String query = SecureChannel.text(request, "player", 64);
        var player = server.getPlayerList().getPlayers().stream().filter(candidate ->
            candidate.getGameProfile().name().equalsIgnoreCase(query) || candidate.getUUID().toString().equalsIgnoreCase(query))
            .findFirst().orElse(null);
        boolean assets = option(request, "include_assets");
        return player == null ? active.offlinePlayer(query, assets, option(request, "online_only"))
            : CompletableFuture.completedFuture(playerSnapshot(player, assets));
    }

    private JsonObject playerSnapshot(ServerPlayer player, boolean assets) {
        var result = playerData(player, assets, true);
        result.addProperty("health", player.getHealth());
        result.addProperty("world", player.level().dimension().identifier().toString());
        result.addProperty("game_mode", player.gameMode().getName().toUpperCase(Locale.ROOT));
        var pos = player.position();
        result.add("position", object(Map.of("position", List.of(pos.x, pos.y, pos.z))).get("position"));
        return result;
    }

    private JsonObject playerData(ServerPlayer player, boolean assets, boolean ping) {
        var result = object(Map.of("name", player.getGameProfile().name(), "uuid", player.getUUID().toString()));
        if (ping) {
            result.addProperty("ping", player.connection.latency());
        }
        if (assets) {
            player.getGameProfile().properties().get("textures").stream().findFirst()
                .ifPresent(property -> profileSkin(result, property.value()));
        }
        return result;
    }

    private JsonObject deliver(MinecraftServer server, EndpointHost active, JsonObject request) {
        var message = Component.literal(active.message(request));
        String recipient = SecureChannel.text(request, "player", 36);
        var players = server.getPlayerList();
        if (recipient.isEmpty()) {
            players.getPlayers().forEach(player -> player.sendSystemMessage(message));
            return object(Map.of("delivered", players.getPlayerCount()));
        }
        var player = players.getPlayer(UUID.fromString(recipient));
        if (player == null) {
            throw new RequestFailure("player_offline", "Player is no longer online");
        }
        player.sendSystemMessage(message);
        return object(Map.of("delivered", 1));
    }

    private JsonObject execute(MinecraftServer server, String command) {
        List<String> lines = new ArrayList<>();
        CommandSource output = new CommandSource() {
            @Override public void sendSystemMessage(Component message) {
                if (lines.size() < 16) {
                    lines.add(limit(message.getString(), 320));
                }
            }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
        boolean[] accepted = {false};
        var source = server.createCommandSourceStack().withSource(output)
            .withCallback((success, count) -> accepted[0] |= success);
        server.getCommands().performPrefixedCommand(source, command);
        return object(Map.of("accepted", accepted[0], "output", lines));
    }

    private void presence(String action, ServerPlayer player) {
        var active = host;
        if (active != null) {
            if (action.equals("player.leave") && active.keepsOfflinePlayers()) {
                active.rememberPlayer(playerSnapshot(player, true));
            }
            if (active.settings().events()) {
                active.publish(action, playerData(player, false, false));
            }
        }
    }

    private int onCommand(CommandSourceStack source, String arguments) {
        var active = host;
        if (active == null) {
            source.sendFailure(Component.literal("AstrBotAdapter is offline"));
            return 0;
        }
        String[] args = arguments.strip().split("\\s+");
        var player = source.getPlayer();
        boolean admin = Commands.hasPermission(Commands.LEVEL_ADMINS).test(source);
        if (!active.backendMode() && player != null && (args[0].equals("ai") || args[0].equals("ai-public"))) {
            boolean publicly = args[0].equals("ai-public");
            String question = arguments.strip().substring(args[0].length()).strip();
            if (!active.settings().ai() || question.isEmpty() || (publicly && !admin)) {
                source.sendFailure(Component.literal("Usage or permission: /astrbot ai <message>"));
                return 0;
            }
            if (active.connections() == 0) {
                source.sendFailure(Component.literal("AstrBot is not connected"));
                return 0;
            }
            var data = playerData(player, false, false);
            data.addProperty("text", limit(question, 1500));
            data.addProperty("public", publicly);
            active.publish("ai.request", data);
            return 1;
        }
        if (!active.backendMode() && player != null && args[0].equals("bind")) {
            if (args.length != 2 || !args[1].matches("[A-Z2-7]{8}") || active.connections() == 0) {
                source.sendFailure(Component.literal("Usage: /astrbot bind <code> (AstrBot must be connected)"));
                return 0;
            }
            var data = playerData(player, false, false);
            data.addProperty("code", args[1]);
            active.publish("player.bind", data);
            source.sendSuccess(() -> Component.literal("Binding submitted to AstrBot"), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal(admin ? active.admin(args) : "/astrbot ai <message> | /astrbot bind <code>"), false);
        return 1;
    }
}
