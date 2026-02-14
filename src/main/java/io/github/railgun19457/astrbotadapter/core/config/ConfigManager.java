package io.github.railgun19457.astrbotadapter.core.config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Configuration manager.
 * Loads platform-specific default config files (config-velocity.yml / config-backend.yml)
 * and preserves YAML comments during save.
 */
public class ConfigManager {

    /**
     * Platform type for determining which default config to use.
     */
    public enum ConfigPlatform {
        /** Bukkit/Paper/Folia backend server */
        BACKEND,
        /** Velocity proxy server */
        VELOCITY
    }

    private static final String CONFIG_FILE_NAME = "config.yml";
    private static final String DEFAULT_BACKEND_CONFIG = "config-backend.yml";
    private static final String DEFAULT_VELOCITY_CONFIG = "config-velocity.yml";

    private final Path dataFolder;
    private final Logger logger;
    private final ConfigPlatform platform;
    private PluginConfig config;
    private Map<String, Object> rawConfig;

    /**
     * Create a config manager for the given platform.
     * @param dataFolder plugin data folder
     * @param logger logger
     * @param platform the platform type (BACKEND or VELOCITY)
     */
    public ConfigManager(Path dataFolder, Logger logger, ConfigPlatform platform) {
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.platform = platform;
        this.config = new PluginConfig();
    }

    /**
     * Legacy constructor for backward compatibility.
     * Defaults to BACKEND platform.
     */
    public ConfigManager(Path dataFolder, Logger logger) {
        this(dataFolder, logger, ConfigPlatform.BACKEND);
    }

    /**
     * Load the configuration file.
     * @return true if loaded successfully
     */
    public boolean loadConfig() {
        Path configFile = dataFolder.resolve(CONFIG_FILE_NAME);

        // Create default config if it doesn't exist
        if (!Files.exists(configFile)) {
            saveDefaultConfig();
        }

        try {
            rawConfig = loadYaml(configFile);
            parseConfig(rawConfig);

            // Validate and fix
            ConfigValidator validator = new ConfigValidator(logger);
            if (!validator.validate(config)) {
                logger.warning("配置文件存在问题，已使用默认值修复");
            }

            // Apply platform-specific defaults that don't need config toggles
            applyPlatformDefaults();

            return true;
        } catch (Exception e) {
            logger.severe("加载配置文件失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * Apply platform-specific defaults.
     * - Velocity: always acts as proxy bridge, never as proxy mode backend
     * - Backend: never acts as proxy bridge
     */
    private void applyPlatformDefaults() {
        switch (platform) {
            case VELOCITY -> {
                // Velocity is always the proxy bridge, never a backend in proxy mode
                config.setProxyBridgeEnabled(true);
                config.setProxyModeEnabled(false);
            }
            case BACKEND -> {
                // Backend can never be a proxy bridge
                config.setProxyBridgeEnabled(false);
                // proxyModeEnabled is read from config - user decides
            }
        }
    }

    /**
     * Reload configuration.
     * @return true if reloaded successfully
     */
    public boolean reloadConfig() {
        return loadConfig();
    }

    /**
     * Save default config file from resources, preserving comments.
     */
    public void saveDefaultConfig() {
        Path configFile = dataFolder.resolve(CONFIG_FILE_NAME);

        try {
            Files.createDirectories(dataFolder);

            // Pick the right default config based on platform
            String resourceName = (platform == ConfigPlatform.VELOCITY)
                    ? DEFAULT_VELOCITY_CONFIG
                    : DEFAULT_BACKEND_CONFIG;

            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
                if (in != null) {
                    Files.copy(in, configFile);
                } else {
                    // Fallback: try the generic config.yml
                    try (InputStream fallback = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE_NAME)) {
                        if (fallback != null) {
                            Files.copy(fallback, configFile);
                        } else {
                            createBasicConfig(configFile);
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.severe("保存默认配置文件失败: " + e.getMessage());
        }
    }

    /**
     * Save current configuration to file.
     * Uses a comment-preserving approach: reads existing file, updates values in-place.
     */
    public void saveConfig() {
        Path configFile = dataFolder.resolve(CONFIG_FILE_NAME);

        try {
            if (Files.exists(configFile)) {
                // Read existing content, update values, and write back preserving comments
                String content = Files.readString(configFile, StandardCharsets.UTF_8);
                content = updateYamlValue(content, "auth", "token", config.getToken());
                Files.writeString(configFile, content, StandardCharsets.UTF_8);
            } else {
                // No existing file - write fresh with buildConfigMap
                try (BufferedWriter writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                    writeYaml(writer, buildConfigMap());
                }
            }
        } catch (IOException e) {
            logger.severe("保存配置文件失败: " + e.getMessage());
        }
    }

    /**
     * Update a specific YAML value in the file content while preserving comments.
     * Handles simple "key: value" patterns under a parent section.
     */
    private String updateYamlValue(String content, String section, String key, String value) {
        String[] lines = content.split("\n", -1);
        StringBuilder result = new StringBuilder();
        boolean inSection = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            // Detect top-level section (no indentation, ends with colon, not a comment)
            if (!line.startsWith(" ") && !line.startsWith("\t") && !trimmed.startsWith("#") && trimmed.endsWith(":") && !trimmed.isEmpty()) {
                String sectionName = trimmed.substring(0, trimmed.length() - 1).trim();
                inSection = sectionName.equals(section);
            }

            // If we're in the target section, look for the key
            if (inSection && trimmed.startsWith(key + ":")) {
                // Preserve indentation
                int indent = line.indexOf(key);
                String prefix = line.substring(0, indent);
                String formattedValue = formatYamlValue(value);
                result.append(prefix).append(key).append(": ").append(formattedValue);
            } else {
                result.append(line);
            }

            if (i < lines.length - 1) {
                result.append("\n");
            }
        }

        return result.toString();
    }

    /**
     * Format a value for YAML output.
     */
    private String formatYamlValue(String value) {
        if (value == null || value.isEmpty()) {
            return "\"\"";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /**
     * Update token and save.
     */
    public void updateToken(String newToken) {
        config.setToken(newToken);
        if (rawConfig != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> auth = (Map<String, Object>) rawConfig.get("auth");
            if (auth != null) {
                auth.put("token", newToken);
            }
        }
        saveConfig();
    }

    /**
     * Get current configuration.
     */
    public PluginConfig getConfig() {
        return config;
    }

    /**
     * Get the platform type.
     */
    public ConfigPlatform getPlatform() {
        return platform;
    }

    /**
     * Parse config map into PluginConfig.
     */
    private void parseConfig(Map<String, Object> yaml) {
        // General
        Map<String, Object> general = getMap(yaml, "general");
        if (general != null) {
            config.setLanguage(getString(general, "language", "zh_CN"));
            config.setDebug(getBoolean(general, "debug", false));
        }

        // Auth
        Map<String, Object> auth = getMap(yaml, "auth");
        if (auth != null) {
            config.setToken(getString(auth, "token", ""));
        }

        // Server
        Map<String, Object> server = getMap(yaml, "server");
        if (server != null) {
            String serverHost = getString(server, "host", null);
            Integer serverPort = getIntOrNull(server, "port");

            if (serverHost != null) {
                config.setServerHost(serverHost);
            }
            if (serverPort != null) {
                config.setServerPort(serverPort);
            }

            // WebSocket
            Map<String, Object> ws = getMap(server, "websocket");
            if (ws != null) {
                config.setWsEnabled(getBoolean(ws, "enabled", true));
                String wsHost = getString(ws, "host", null);
                Integer wsPort = getIntOrNull(ws, "port");
                if (wsHost != null) {
                    config.setWsHost(wsHost);
                    if (serverHost == null) {
                        config.setServerHost(wsHost);
                    }
                }
                if (wsPort != null) {
                    config.setWsPort(wsPort);
                    if (serverPort == null) {
                        config.setServerPort(wsPort);
                    }
                }
                config.setHeartbeatInterval(getInt(ws, "heartbeatInterval", 30));
                config.setHeartbeatTimeout(getInt(ws, "heartbeatTimeout", 90));
            }

            // REST API
            Map<String, Object> rest = getMap(server, "restapi");
            if (rest != null) {
                config.setRestEnabled(getBoolean(rest, "enabled", true));
                String restHost = getString(rest, "host", null);
                Integer restPort = getIntOrNull(rest, "port");
                if (restHost != null) {
                    config.setRestHost(restHost);
                }
                if (restPort != null) {
                    config.setRestPort(restPort);
                }
                config.setRateLimit(getInt(rest, "rateLimit", 100));
            }
        }

        // Message Forward
        Map<String, Object> forward = getMap(yaml, "messageForward");
        if (forward != null) {
            config.setForwardEnabled(getBoolean(forward, "enabled", true));
            config.setForwardPrefix(getString(forward, "prefix", "*"));
            config.setStripPrefix(getBoolean(forward, "stripPrefix", true));
            config.setIncomingFormat(getString(forward, "incomingFormat",
                    "§7[§b{platform}§7] §f{username}§7: §f{content}"));
        }

        // AI Chat (backend only, but parse if present for compatibility)
        Map<String, Object> aiChat = getMap(yaml, "aiChat");
        if (aiChat != null) {
            Map<String, Object> group = getMap(aiChat, "group");
            if (group != null) {
                config.setGroupChatEnabled(getBoolean(group, "enabled", true));
                config.setGroupChatPrefix(getString(group, "prefix", "@"));
            }

            Map<String, Object> priv = getMap(aiChat, "private");
            if (priv != null) {
                config.setPrivateChatEnabled(getBoolean(priv, "enabled", true));
                config.setPrivateChatPrefix(getString(priv, "prefix", "#"));
                config.setPrivateChatEchoFormat(getString(priv, "echoFormat", "<{player}> {message}"));
            }

            config.setAiResponseFormat(getString(aiChat, "responseFormat", "§7[§dAI§7] §f{content}"));
            config.setAiThinkingMessage(getString(aiChat, "thinkingMessage", "§7[§dAI§7] §e思考中..."));
            config.setAiShowThinking(getBoolean(aiChat, "showThinking", true));
            config.setAiTimeoutSeconds(getInt(aiChat, "timeout", 60));
        }

        // Player Notification
        Map<String, Object> notify = getMap(yaml, "playerNotification");
        if (notify != null) {
            Map<String, Object> join = getMap(notify, "join");
            if (join != null) {
                config.setJoinNotifyEnabled(getBoolean(join, "enabled", true));
            }
            Map<String, Object> quit = getMap(notify, "quit");
            if (quit != null) {
                config.setQuitNotifyEnabled(getBoolean(quit, "enabled", true));
            }
        }

        // Command Execution
        Map<String, Object> cmd = getMap(yaml, "commandExecution");
        if (cmd != null) {
            config.setCommandEnabled(getBoolean(cmd, "enabled", true));
            config.setCommandFilterMode(getString(cmd, "filterType", "NONE"));
            config.setCommandFilterList(getStringList(cmd, "commandList"));
        }

        // Log Query
        Map<String, Object> logQuery = getMap(yaml, "logQuery");
        if (logQuery != null) {
            config.setLogQueryEnabled(getBoolean(logQuery, "enabled", true));
            config.setLogQueryMaxLines(getInt(logQuery, "maxLines", 1000));
            config.setLogQueryFile(getString(logQuery, "logFile", ""));
        }

        // Update Check (backend only)
        Map<String, Object> update = getMap(yaml, "updateCheck");
        if (update != null) {
            config.setUpdateCheckEnabled(getBoolean(update, "enabled", true));
            config.setUpdateNotifyOps(getBoolean(update, "notifyOps", true));
        }

        // Proxy Mode (backend only - the enabled toggle matters)
        Map<String, Object> proxyMode = getMap(yaml, "proxyMode");
        if (proxyMode != null) {
            config.setProxyModeEnabled(getBoolean(proxyMode, "enabled", false));
            config.setProxySecret(getString(proxyMode, "secret", ""));
        }

        // Proxy Bridge (legacy compat - now derived from platform in applyPlatformDefaults)
        Map<String, Object> proxyBridge = getMap(yaml, "proxyBridge");
        if (proxyBridge != null) {
            config.setProxyBridgeEnabled(getBoolean(proxyBridge, "enabled", false));
        }
    }

    // ===== YAML Tools =====

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadYaml(Path file) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream in = Files.newInputStream(file)) {
            Object loaded = yaml.load(in);
            if (loaded instanceof Map) {
                return (Map<String, Object>) loaded;
            }
        }
        return new java.util.LinkedHashMap<>();
    }

    private void writeYaml(BufferedWriter writer, Map<String, Object> data) throws IOException {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        Yaml yaml = new Yaml(options);
        yaml.dump(data, writer);
    }

    private Map<String, Object> buildConfigMap() {
        java.util.LinkedHashMap<String, Object> root = new java.util.LinkedHashMap<>();

        java.util.LinkedHashMap<String, Object> general = new java.util.LinkedHashMap<>();
        general.put("language", config.getLanguage());
        general.put("debug", config.isDebug());
        root.put("general", general);

        java.util.LinkedHashMap<String, Object> auth = new java.util.LinkedHashMap<>();
        auth.put("token", config.getToken());
        root.put("auth", auth);

        java.util.LinkedHashMap<String, Object> server = new java.util.LinkedHashMap<>();
        server.put("host", config.getServerHost());
        server.put("port", config.getServerPort());

        java.util.LinkedHashMap<String, Object> ws = new java.util.LinkedHashMap<>();
        ws.put("enabled", config.isWsEnabled());
        ws.put("heartbeatInterval", config.getHeartbeatInterval());
        ws.put("heartbeatTimeout", config.getHeartbeatTimeout());
        server.put("websocket", ws);

        java.util.LinkedHashMap<String, Object> rest = new java.util.LinkedHashMap<>();
        rest.put("enabled", config.isRestEnabled());
        rest.put("rateLimit", config.getRateLimit());
        server.put("restapi", rest);
        root.put("server", server);

        return root;
    }

    private void createBasicConfig(Path file) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writeYaml(writer, buildConfigMap());
        }
    }

    // ===== Utility Methods =====

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    private String getString(Map<String, Object> map, String key, String def) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : def;
    }

    private boolean getBoolean(Map<String, Object> map, String key, boolean def) {
        Object value = map.get(key);
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        return def;
    }

    private int getInt(Map<String, Object> map, String key, int def) {
        Object value = map.get(key);
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    private Integer getIntOrNull(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private List<String> getStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            java.util.ArrayList<String> result = new java.util.ArrayList<>();
            for (Object item : list) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        return new java.util.ArrayList<>();
    }
}
