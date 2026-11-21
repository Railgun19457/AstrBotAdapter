package io.github.railgun19457.astrbotadapter.core.config;

import io.github.railgun19457.astrbotadapter.core.FeatureConfig;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformAdapter;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConfigService {
    private final PlatformAdapter platform;

    public ConfigService(PlatformAdapter platform) {
        this.platform = platform;
    }

    public AdapterConfig loadOrCreate() {
        Path configPath = platform.configStore().dataDirectory().resolve("config.yml");
        AdapterConfig defaults = AdapterConfig.defaults(platform.runtimeMode(), platform.platformInfo().serverId(), platform.platformInfo().displayName());
        if (!Files.exists(configPath)) {
            saveDefault(configPath, defaults);
            return defaults;
        }
        try (Reader reader = Files.newBufferedReader(configPath)) {
            Object loaded = new Yaml().load(reader);
            if (!(loaded instanceof Map<?, ?> map)) {
                return defaults;
            }
            return fromMap(map, defaults);
        } catch (IOException exception) {
            platform.logger().error("Failed to load config.yml, using defaults", exception);
            return defaults;
        }
    }

    private void saveDefault(Path path, AdapterConfig defaults) {
        try {
            Files.createDirectories(path.getParent());
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            try (Writer writer = Files.newBufferedWriter(path)) {
                new Yaml(options).dump(toMap(defaults), writer);
            }
        } catch (IOException exception) {
            platform.logger().error("Failed to write default config.yml", exception);
        }
    }

    private AdapterConfig fromMap(Map<?, ?> map, AdapterConfig defaults) {
        String serverId = string(map, "server_id", defaults.serverId());
        String displayName = string(map, "display_name", defaults.displayName());
        return new AdapterConfig(
            defaults.mode(),
            serverId,
            displayName,
            websocket(child(map, "standalone_websocket"), defaults.standaloneWebSocket()),
            websocket(child(map, "astrbot_websocket"), defaults.astrbotWebSocket()),
            websocket(child(map, "cluster_websocket"), defaults.clusterWebSocket()),
            velocityBackend(child(map, "velocity_backend"), defaults.velocityBackend()),
            auth(child(map, "auth"), defaults.auth()),
            features(child(map, "features"), defaults.features()),
            chat(child(map, "chat"), defaults.chat()),
            ai(child(map, "ai"), defaults.ai()),
            commandPolicy(child(map, "command_policy"), defaults.commandPolicy()),
            rateLimit(child(map, "rate_limit"), defaults.rateLimit()),
            locale(child(map, "locale"), defaults.locale())
        );
    }

    private VelocityBackendConfig velocityBackend(Map<?, ?> map, VelocityBackendConfig defaults) {
        if (map.isEmpty()) {
            return defaults;
        }
        return new VelocityBackendConfig(
            string(map, "backend_id", defaults.backendId()),
            string(map, "velocity_url", defaults.velocityUrl())
        );
    }

    private AuthConfig auth(Map<?, ?> map, AuthConfig defaults) {
        if (map.isEmpty()) {
            return defaults;
        }
        return new AuthConfig(
            string(map, "key_id", defaults.keyId()),
            bool(map, "enabled", defaults.enabled()),
            string(map, "keys_directory", defaults.keysDirectory()),
            longValue(map, "auth_code_ttl_millis", defaults.authCodeTtlMillis())
        );
    }

    private FeatureConfig features(Map<?, ?> map, FeatureConfig defaults) {
        if (map.isEmpty()) {
            return defaults;
        }
        return new FeatureConfig(
            bool(map, "chat", defaults.chat()),
            bool(map, "player", defaults.player()),
            bool(map, "server", defaults.server()),
            bool(map, "command", defaults.command()),
            bool(map, "ai", defaults.ai()),
            bool(map, "log", defaults.log()),
            bool(map, "cluster", defaults.cluster()),
            bool(map, "config", defaults.config())
        );
    }

    private ChatConfig chat(Map<?, ?> map, ChatConfig defaults) {
        if (map.isEmpty()) {
            return defaults;
        }
        return new ChatConfig(
            bool(map, "forward_enabled", defaults.forwardEnabled()),
            string(map, "forward_prefix", defaults.forwardPrefix()),
            string(map, "minecraft_to_external_format", defaults.minecraftToExternalFormat()),
            string(map, "external_to_minecraft_format", defaults.externalToMinecraftFormat()),
            bool(map, "join_forward_enabled", defaults.joinForwardEnabled()),
            bool(map, "leave_forward_enabled", defaults.leaveForwardEnabled())
        );
    }

    private AiConfig ai(Map<?, ?> map, AiConfig defaults) {
        if (map.isEmpty()) {
            return defaults;
        }
        return new AiConfig(
            bool(map, "group_enabled", defaults.groupEnabled()),
            string(map, "group_prefix", defaults.groupPrefix()),
            bool(map, "private_enabled", defaults.privateEnabled()),
            string(map, "private_prefix", defaults.privatePrefix())
        );
    }

    private CommandPolicyConfig commandPolicy(Map<?, ?> map, CommandPolicyConfig defaults) {
        if (map.isEmpty()) {
            return defaults;
        }
        return new CommandPolicyConfig(
            bool(map, "enabled", defaults.enabled()),
            stringList(map, "allowlist", defaults.allowlist()),
            stringList(map, "denylist", defaults.denylist()),
            bool(map, "allow_all_servers", defaults.allowAllServers())
        );
    }

    private RateLimitConfig rateLimit(Map<?, ?> map, RateLimitConfig defaults) {
        if (map.isEmpty()) {
            return defaults;
        }
        return new RateLimitConfig(
            integer(map, "max_pending_requests", defaults.maxPendingRequests()),
            integer(map, "max_send_queue", defaults.maxSendQueue()),
            integer(map, "max_frame_bytes", defaults.maxFrameBytes()),
            longValue(map, "heartbeat_interval_millis", defaults.heartbeatIntervalMillis()),
            integer(map, "heartbeat_timeouts_before_close", defaults.heartbeatTimeoutsBeforeClose())
        );
    }

    private LocaleConfig locale(Map<?, ?> map, LocaleConfig defaults) {
        if (map.isEmpty()) {
            return defaults;
        }
        return new LocaleConfig(
            string(map, "language", defaults.language()),
            bool(map, "update_check", defaults.updateCheck())
        );
    }

    private WebSocketConfig websocket(Map<?, ?> map, WebSocketConfig defaults) {
        if (map.isEmpty()) {
            return defaults;
        }
        return new WebSocketConfig(
            bool(map, "enabled", defaults.enabled()),
            string(map, "host", defaults.host()),
            integer(map, "port", defaults.port()),
            string(map, "path", defaults.path()),
            string(map, "url", defaults.url())
        );
    }

    private Map<String, Object> toMap(AdapterConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("mode", config.mode().name().toLowerCase());
        map.put("server_id", config.serverId());
        map.put("display_name", config.displayName());
        map.put("standalone_websocket", websocketMap(config.standaloneWebSocket()));
        map.put("astrbot_websocket", websocketMap(config.astrbotWebSocket()));
        map.put("cluster_websocket", websocketMap(config.clusterWebSocket()));
        map.put("velocity_backend", velocityBackendMap(config.velocityBackend()));
        map.put("auth", authMap(config.auth()));
        map.put("features", featuresMap(config.features()));
        map.put("chat", chatMap(config.chat()));
        map.put("ai", aiMap(config.ai()));
        map.put("command_policy", commandPolicyMap(config.commandPolicy()));
        map.put("rate_limit", rateLimitMap(config.rateLimit()));
        map.put("locale", localeMap(config.locale()));
        return map;
    }

    private Map<String, Object> websocketMap(WebSocketConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", config.enabled());
        map.put("host", config.host());
        map.put("port", config.port());
        map.put("path", config.path());
        map.put("url", config.url());
        return map;
    }

    private Map<String, Object> velocityBackendMap(VelocityBackendConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("backend_id", config.backendId());
        map.put("velocity_url", config.velocityUrl());
        return map;
    }

    private Map<String, Object> authMap(AuthConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key_id", config.keyId());
        map.put("enabled", config.enabled());
        map.put("keys_directory", config.keysDirectory());
        map.put("auth_code_ttl_millis", config.authCodeTtlMillis());
        return map;
    }

    private Map<String, Object> featuresMap(FeatureConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("chat", config.chat());
        map.put("player", config.player());
        map.put("server", config.server());
        map.put("command", config.command());
        map.put("ai", config.ai());
        map.put("log", config.log());
        map.put("cluster", config.cluster());
        map.put("config", config.config());
        return map;
    }

    private Map<String, Object> chatMap(ChatConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("forward_enabled", config.forwardEnabled());
        map.put("forward_prefix", config.forwardPrefix());
        map.put("minecraft_to_external_format", config.minecraftToExternalFormat());
        map.put("external_to_minecraft_format", config.externalToMinecraftFormat());
        map.put("join_forward_enabled", config.joinForwardEnabled());
        map.put("leave_forward_enabled", config.leaveForwardEnabled());
        return map;
    }

    private Map<String, Object> aiMap(AiConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("group_enabled", config.groupEnabled());
        map.put("group_prefix", config.groupPrefix());
        map.put("private_enabled", config.privateEnabled());
        map.put("private_prefix", config.privatePrefix());
        return map;
    }

    private Map<String, Object> commandPolicyMap(CommandPolicyConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", config.enabled());
        map.put("allowlist", config.allowlist());
        map.put("denylist", config.denylist());
        map.put("allow_all_servers", config.allowAllServers());
        return map;
    }

    private Map<String, Object> rateLimitMap(RateLimitConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("max_pending_requests", config.maxPendingRequests());
        map.put("max_send_queue", config.maxSendQueue());
        map.put("max_frame_bytes", config.maxFrameBytes());
        map.put("heartbeat_interval_millis", config.heartbeatIntervalMillis());
        map.put("heartbeat_timeouts_before_close", config.heartbeatTimeoutsBeforeClose());
        return map;
    }

    private Map<String, Object> localeMap(LocaleConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("language", config.language());
        map.put("update_check", config.updateCheck());
        return map;
    }

    private Map<?, ?> child(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof Map<?, ?> child ? child : Map.of();
    }

    private String string(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private boolean bool(Map<?, ?> map, String key, boolean fallback) {
        Object value = map.get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    private int integer(Map<?, ?> map, String key, int fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private long longValue(Map<?, ?> map, String key, long fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private List<String> stringList(Map<?, ?> map, String key, List<String> fallback) {
        Object value = map.get(key);
        if (!(value instanceof List<?> list)) {
            return fallback;
        }
        return list.stream().map(String::valueOf).toList();
    }
}
