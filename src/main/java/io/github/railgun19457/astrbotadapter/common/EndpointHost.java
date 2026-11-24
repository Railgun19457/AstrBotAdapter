package io.github.railgun19457.astrbotadapter.common;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.railgun19457.astrbotadapter.protocol.SecureChannel;
import io.github.railgun19457.astrbotadapter.transport.AdapterServer;
import io.github.railgun19457.astrbotadapter.transport.AdapterServer.RequestFailure;
import io.github.railgun19457.astrbotadapter.transport.BackendLink;
import io.github.railgun19457.astrbotadapter.transport.TrustStore;

import java.io.IOException;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class EndpointHost implements AutoCloseable {
    private static final Gson JSON = new Gson();
    private static final Pattern SKIN_URL = Pattern.compile("https?://textures\\.minecraft\\.net/texture/([0-9a-f]{32,64})");
    private static final Pattern CHAT_FIELD = Pattern.compile("\\{(?:platform|sender|message)\\}");
    private final AdapterServer link;
    private final TrustStore trust;
    private final TrustStore backendTrust;
    private final BackendLink upstream;
    private final ConfigReader readConfig;
    private final ServerDiagnostics diagnostics;
    private final Path managedFile;
    private final Predicate<String> registered;
    private final String role;
    private final String defaultId;
    private final String defaultCommand;
    private volatile Settings settings;
    private final Map<String, CompletableFuture<JsonObject>> recentCommands = new LinkedHashMap<>();
    private final Logger logger;
    private final long started = System.nanoTime();
    public final String id;

    public EndpointHost(Path folder, String role, String defaultId, int defaultPort, Logger logger,
                        AdapterServer.Handler handler, Predicate<String> registered) throws IOException, GeneralSecurityException {
        this(folder, role, defaultId, defaultPort, logger, handler, registered, null);
    }

    public EndpointHost(Path folder, String role, String defaultId, int defaultPort, Logger logger,
                        AdapterServer.Handler handler, Predicate<String> registered, ConfigReader reader)
            throws IOException, GeneralSecurityException {
        this.logger = logger;
        this.role = role;
        this.defaultId = defaultId;
        this.registered = registered;
        Files.createDirectories(folder);
        managedFile = folder.resolve("managed-runtime.properties");
        diagnostics = new ServerDiagnostics(folder, logger);
        defaultCommand = role.equals("velocity") ? "velocity" : "list";
        Path file = folder.resolve("config.properties");
        if (reader == null && Files.notExists(file)) {
            Files.writeString(file, """
                node.id=%s
                node.name=%s
                transport.host=127.0.0.1
                transport.port=%d
                transport.path=/astrbot
                features.chat=true
                features.ai=true
                features.player_events=true
                features.offline_players=true
                features.log_query=false
                chat.prefix=*
                chat.incoming_format=[{platform}] {sender}: {message}
                commands.enabled=false
                commands.allow=%s
                # Standalone servers can join a Velocity proxy by setting backend.url and backend.proxy_id.
                """.formatted(defaultId, defaultId, defaultPort, defaultCommand));
        }
        readConfig = reader != null ? reader : () -> {
            Properties properties = new Properties();
            try (var source = Files.newBufferedReader(file)) {
                properties.load(source);
            }
            return properties;
        };
        Properties config = readConfig.load();
        id = SecureChannel.nodeId(config.getProperty("node.id", defaultId));
        settings = loadSettings(config, managedSettings());
        if (!role.equals("velocity") && !config.getProperty("backend.url", "").isBlank()) {
            trust = null;
            backendTrust = null;
            link = null;
            upstream = new BackendLink(id, config, new TrustStore(folder.resolve("upstream-keys")), handler, logger);
            return;
        }
        upstream = null;
        var address = InetAddress.getByName(config.getProperty("transport.host", "127.0.0.1"));
        int port = Integer.parseInt(config.getProperty("transport.port", String.valueOf(defaultPort)));
        String path = config.getProperty("transport.path", "/astrbot");
        if (!address.isLoopbackAddress() || port < 1 || port > 65535 || !path.matches("/[A-Za-z0-9/_-]{1,127}")) {
            throw new IllegalArgumentException("Expected a loopback WebSocket address, port and path");
        }
        AdapterServer.Backends backends = null;
        if (role.equals("velocity")) {
            String backendPath = config.getProperty("transport.backend_path", "/backend");
            if (path.equals(backendPath) || !backendPath.matches("/[A-Za-z0-9/_-]{1,127}")) {
                throw new IllegalArgumentException("Invalid Velocity backend path");
            }
            backendTrust = new TrustStore(folder.resolve("backend-keys"));
            backends = new AdapterServer.Backends(backendPath, backendTrust, registered);
        } else {
            backendTrust = null;
        }
        trust = new TrustStore(folder.resolve("keys"));
        link = new AdapterServer(new InetSocketAddress(address, port), path, id, role, trust, capabilities(),
            handler, logger, backends);
    }

    private List<String> capabilities() {
        var capabilities = new ArrayList<>(List.of("server.status", "player.list", "player.detail", "player.bind",
            "chat.send", "command.run", "log.query"));
        if (role.equals("velocity")) {
            capabilities.add("server.list");
        }
        return capabilities;
    }

    private Properties managedSettings() throws IOException {
        Properties managed = new Properties();
        if (Files.isRegularFile(managedFile)) {
            try (var file = Files.newBufferedReader(managedFile)) {
                managed.load(file);
            }
        }
        return managed;
    }

    private Settings loadSettings(Properties config, Properties managed) {
        if (flag(config, "backend.allow_config", false) && !config.getProperty("backend.url", "").isBlank()) {
            config.putAll(managed);
        }
        return new Settings(config, config.getProperty("node.name", id), config.getProperty("chat.prefix", "*"),
            flag(config, "features.chat", true), flag(config, "features.ai", true), flag(config, "features.player_events", true),
            flag(config, "commands.enabled", false) ? commandRoots(config.getProperty("commands.allow", defaultCommand)) : Set.of());
    }

    public Settings settings() {
        return settings;
    }

    public boolean keepsOfflinePlayers() {
        return flag(settings.properties, "features.offline_players", true);
    }

    public void rememberPlayer(JsonObject snapshot) {
        diagnostics.save(snapshot);
    }

    public CompletableFuture<JsonObject> offlinePlayer(String player, boolean assets, boolean onlineRequired) {
        if (onlineRequired || !keepsOfflinePlayers()) {
            return CompletableFuture.failedFuture(new RequestFailure("player_offline", "Player is not online"));
        }
        return diagnostics.find(player, assets);
    }

    public CompletableFuture<JsonObject> logs(JsonObject data) {
        if (!flag(settings.properties, "features.log_query", false)) {
            throw new RequestFailure("disabled", "Log queries are disabled on this server");
        }
        return diagnostics.tail(data);
    }

    public synchronized JsonObject updateConfig(JsonObject data) {
        if (!backendMode() || !flag(settings.properties, "backend.allow_config", false)) {
            throw new RequestFailure("disabled", "The backend has not enabled remote runtime settings");
        }
        String key = SecureChannel.text(data, "key", 64);
        String value = SecureChannel.text(data, "value", 256);
        boolean reset = option(data, "reset");
        switch (key) {
            case "node.name" -> {
                if (!reset && (value.isBlank() || value.chars().anyMatch(Character::isISOControl))) {
                    throw new IllegalArgumentException("Invalid server name");
                }
            }
            case "features.offline_players", "features.log_query", "commands.enabled" -> {
                if (!reset && !Set.of("true", "false").contains(value)) {
                    throw new IllegalArgumentException("Expected true or false");
                }
            }
            case "commands.allow" -> {
                if (!reset && commandRoots(value).stream().anyMatch(root -> !root.matches("[a-z0-9_.-]+(?::[a-z0-9_.-]+)?"))) {
                    throw new IllegalArgumentException("Expected comma-separated command names");
                }
            }
            default -> throw new IllegalArgumentException("This setting is local-only");
        }
        try {
            var local = readConfig.load();
            if (!flag(local, "backend.allow_config", false) || local.getProperty("backend.url", "").isBlank()) {
                throw new RequestFailure("disabled", "The backend has disabled remote runtime settings");
            }
            var managed = managedSettings();
            if (reset) {
                managed.remove(key);
            } else {
                managed.setProperty(key, value);
            }
            var next = loadSettings(local, managed);
            var text = new StringWriter();
            managed.store(text, "Runtime overrides managed by Velocity");
            ServerDiagnostics.write(managedFile, text.toString());
            settings = next;
            return object(Map.of("updated", true, "key", key));
        } catch (IOException error) {
            throw new RequestFailure("config_unavailable", "Could not persist the backend setting");
        }
    }

    public boolean backendMode() {
        return upstream != null;
    }

    public boolean backendReady(String target) {
        return link != null && link.backendReady(target);
    }

    public boolean backendPaired(String target) {
        return backendTrust != null && backendTrust.keyFor(target) != null;
    }

    public CompletionStage<JsonObject> backendRequest(String target, String client, String id, String operation, JsonObject data) {
        return link.requestBackend(target, client, id, operation, data);
    }

    public void start() {
        if (upstream != null) {
            upstream.start();
        } else {
            link.start();
        }
    }

    @Override
    public void close() {
        if (upstream != null) {
            upstream.close();
        } else {
            link.shutdown();
        }
        diagnostics.close();
    }

    public int connections() {
        return link == null ? 0 : link.connections();
    }

    public long uptime() {
        return (System.nanoTime() - started) / 1_000_000_000;
    }

    public void publish(String operation, JsonObject data) {
        if (link != null) {
            link.publish(operation, data);
        }
    }

    public synchronized String admin(String[] args) {
        try {
            if (args.length == 1 && args[0].equals("status")) {
                return id + " [" + (upstream != null ? upstream.description()
                    : role + ", " + connections() + " authenticated AstrBot connections") + "]";
            }
            if (args.length == 1 && args[0].equals("connections")) {
                if (upstream != null) {
                    return upstream.description();
                }
                var details = link.connectionDetails();
                return details.isEmpty() ? "No authenticated connections" : String.join("\n", details);
            }
            if (args.length == 1 && args[0].equals("reload")) {
                Properties updated = readConfig.load();
                if (!id.equals(updated.getProperty("node.id", defaultId))) {
                    return "Node ID changes require a restart";
                }
                settings = loadSettings(updated, managedSettings());
                return "Runtime configuration reloaded. Listener and upstream changes require a restart.";
            }
            if (trust != null) {
                if (args.length == 4 && args[0].equals("pair") && args[1].equals("create")) {
                    TrustStore target = trustFor(args[2]);
                    if (target == backendTrust && !registered.test(args[3])) {
                        return "Register this backend ID in Velocity before pairing";
                    }
                    var pairing = target.create(args[3]);
                    return "pairing_id=" + pairing.id() + " code=" + pairing.code();
                }
                if (args.length == 3 && args[0].equals("pair") && args[1].equals("revoke")) {
                    return trust.cancel(args[2]) || backendTrust != null && backendTrust.cancel(args[2])
                        ? "Pairing revoked" : "Pairing not found";
                }
                if (args.length == 2 && args[0].equals("trust") && args[1].equals("list")) {
                    String clients = "AstrBot: " + String.join(", ", trust.clients());
                    return backendTrust == null ? clients : clients + "\nBackends: " + String.join(", ", backendTrust.clients());
                }
                if (args.length == 4 && args[0].equals("trust") && args[1].equals("revoke")) {
                    if (trustFor(args[2]).revoke(args[3])) {
                        link.disconnect(args[2], args[3]);
                        return "Trust revoked";
                    }
                    return "Client not found";
                }
            }
            String pairing = backendTrust == null ? "astrbot" : "astrbot|backend";
            return "/astrbot status | connections | reload" + (trust == null ? "" : " | pair create " + pairing
                + " <node> | pair revoke <id> | trust list | trust revoke " + pairing + " <node>");
        } catch (IOException | IllegalArgumentException exception) {
            return "Could not complete the operation: " + exception.getMessage();
        }
    }

    private TrustStore trustFor(String peerRole) {
        if (peerRole.equals("astrbot")) {
            return trust;
        }
        if (peerRole.equals("backend") && backendTrust != null) {
            return backendTrust;
        }
        throw new IllegalArgumentException("Unsupported peer role");
    }

    public String message(JsonObject data) {
        String message = SecureChannel.text(data, "text", 1500);
        if (message.isBlank()) {
            throw new IllegalArgumentException("Empty chat message");
        }
        String recipient = SecureChannel.text(data, "player", 36);
        Settings current = settings;
        return switch (SecureChannel.text(data, "kind", 16)) {
            case "ai" -> {
                if (!current.ai()) {
                    throw new RequestFailure("disabled", "AI chat is disabled");
                }
                yield "[AI] " + message;
            }
            case "bridge" -> {
                if (!current.chat() || !recipient.isEmpty()) {
                    throw new RequestFailure("disabled", "Chat bridge is disabled or target is invalid");
                }
                var fields = Map.of("{platform}", SecureChannel.text(data, "platform", 40),
                    "{sender}", SecureChannel.text(data, "sender", 80), "{message}", message);
                yield CHAT_FIELD
                    .matcher(current.property("chat.incoming_format", "[{platform}] {sender}: {message}"))
                    .replaceAll(match -> java.util.regex.Matcher.quoteReplacement(fields.get(match.group())));
            }
            default -> throw new IllegalArgumentException("Unknown chat kind");
        };
    }

    public CompletionStage<JsonObject> runCommand(String client, String requestId, JsonObject data,
             Function<String, CompletionStage<JsonObject>> execute) {
        return runCommand(client, requestId, data, settings.commands(), execute);
    }

    public synchronized CompletionStage<JsonObject> runCommand(String client, String requestId, JsonObject data,
             Set<String> allowed, Function<String, CompletionStage<JsonObject>> execute) {
        String command = SecureChannel.text(data, "command", 256).strip();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        String root = command.split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (!allowed.contains(root) || command.chars().anyMatch(Character::isISOControl)) {
            throw new RequestFailure("command_denied", "This command is not allowed");
        }
        String key = client + ":" + target(data) + ":" + requestId;
        var existing = recentCommands.get(key);
        if (existing != null) {
            return existing;
        }
        if (recentCommands.size() >= 512) {
            var iterator = recentCommands.entrySet().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().getValue().isDone()) {
                    iterator.remove();
                    break;
                }
            }
            if (recentCommands.size() >= 512) {
                throw new RequestFailure("busy", "The command queue is full");
            }
        }
        CompletableFuture<JsonObject> result = new CompletableFuture<>();
        recentCommands.put(key, result);
        logger.info("Remote console command " + root + " from " + client);
        try {
            execute.apply(command).whenComplete((output, failure) -> {
                if (failure == null) {
                    result.complete(output);
                } else {
                    var cause = failure.getCause() == null ? failure : failure.getCause();
                    result.completeExceptionally(cause instanceof RequestFailure ? cause
                        : new RequestFailure("command_unknown", "Command result is unknown"));
                }
            });
        } catch (RuntimeException exception) {
            result.completeExceptionally(new RequestFailure("command_unknown", "Command result is unknown"));
        }
        return result;
    }

    private static boolean flag(Properties config, String key, boolean fallback) {
        return Boolean.parseBoolean(config.getProperty(key, Boolean.toString(fallback)));
    }

    public static Set<String> commandRoots(String value) {
        return Arrays.stream(value.split(",")).map(String::strip).filter(item -> !item.isEmpty())
            .map(item -> item.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    public record Settings(Properties properties, String name, String prefix, boolean chat, boolean ai, boolean events,
                           Set<String> commands) {
        public String property(String key, String fallback) {
            return properties.getProperty(key, fallback);
        }
    }

    @FunctionalInterface
    public interface ConfigReader {
        Properties load() throws IOException;
    }

    public static boolean option(JsonObject data, String key) {
        var value = data.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
    }

    public static String target(JsonObject data) {
        return data.has("target") ? SecureChannel.text(data, "target", 64) : "";
    }

    public static JsonObject object(Map<String, ?> map) {
        return JSON.toJsonTree(map).getAsJsonObject();
    }

    public static String limit(String value, int max) {
        int count = value.codePointCount(0, value.length());
        return count <= max ? value : value.substring(0, value.offsetByCodePoints(0, max));
    }

    public static void skin(JsonObject player, String url) {
        if (url != null) {
            var match = SKIN_URL.matcher(url);
            if (match.matches()) {
                player.addProperty("skin", match.group(1));
            }
        }
    }

    public static void profileSkin(JsonObject player, String properties) {
        if (properties.length() <= 4096) {
            try {
                var data = JsonParser.parseString(new String(Base64.getDecoder().decode(properties), java.nio.charset.StandardCharsets.UTF_8));
                skin(player, data.getAsJsonObject().getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString());
            } catch (RuntimeException ignored) {
                // An incomplete texture property has no avatar.
            }
        }
    }
}
