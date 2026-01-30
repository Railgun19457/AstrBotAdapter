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
 * 配置管理器
 * 负责加载、保存、验证和热重载配置文件
 */
public class ConfigManager {

    private final Path dataFolder;
    private final Logger logger;
    private PluginConfig config;
    private Map<String, Object> rawConfig;

    public ConfigManager(Path dataFolder, Logger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.config = new PluginConfig();
    }

    /**
     * 加载配置文件
     * @return 是否加载成功
     */
    public boolean loadConfig() {
        Path configFile = dataFolder.resolve("config.yml");
        
        // 如果配置文件不存在，创建默认配置
        if (!Files.exists(configFile)) {
            saveDefaultConfig();
        }

        try {
            rawConfig = loadYaml(configFile);
            parseConfig(rawConfig);
            
            // 验证并修复配置
            ConfigValidator validator = new ConfigValidator(logger);
            if (!validator.validate(config)) {
                logger.warning("配置文件存在问题，已使用默认值修复");
            }
            
            return true;
        } catch (Exception e) {
            logger.severe("加载配置文件失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 重载配置
     * @return 是否重载成功
     */
    public boolean reloadConfig() {
        return loadConfig();
    }

    /**
     * 保存默认配置文件
     */
    public void saveDefaultConfig() {
        Path configFile = dataFolder.resolve("config.yml");
        
        try {
            Files.createDirectories(dataFolder);
            
            // 从资源中读取默认配置
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                if (in != null) {
                    Files.copy(in, configFile);
                } else {
                    // 如果资源不存在，创建基本配置
                    createBasicConfig(configFile);
                }
            }
        } catch (IOException e) {
            logger.severe("保存默认配置文件失败: " + e.getMessage());
        }
    }

    /**
     * 保存当前配置到文件
     */
    public void saveConfig() {
        Path configFile = dataFolder.resolve("config.yml");
        
        try (BufferedWriter writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
            writeYaml(writer, buildConfigMap());
        } catch (IOException e) {
            logger.severe("保存配置文件失败: " + e.getMessage());
        }
    }

    /**
     * 更新Token并保存
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
     * 获取当前配置
     */
    public PluginConfig getConfig() {
        return config;
    }

    /**
     * 解析配置Map到PluginConfig对象
     */
    private void parseConfig(Map<String, Object> yaml) {
        // 基础设置
        Map<String, Object> general = getMap(yaml, "general");
        if (general != null) {
            config.setLanguage(getString(general, "language", "zh_CN"));
            config.setDebug(getBoolean(general, "debug", false));
        }

        // 认证设置
        Map<String, Object> auth = getMap(yaml, "auth");
        if (auth != null) {
            config.setToken(getString(auth, "token", ""));
        }

        // 服务器设置
        Map<String, Object> server = getMap(yaml, "server");
        if (server != null) {
            // WebSocket
            Map<String, Object> ws = getMap(server, "websocket");
            if (ws != null) {
                config.setWsEnabled(getBoolean(ws, "enabled", true));
                config.setWsHost(getString(ws, "host", "0.0.0.0"));
                config.setWsPort(getInt(ws, "port", 8765));
                config.setHeartbeatInterval(getInt(ws, "heartbeatInterval", 30));
                config.setHeartbeatTimeout(getInt(ws, "heartbeatTimeout", 90));
            }

            // REST API
            Map<String, Object> rest = getMap(server, "restapi");
            if (rest != null) {
                config.setRestEnabled(getBoolean(rest, "enabled", true));
                config.setRestHost(getString(rest, "host", "0.0.0.0"));
                config.setRestPort(getInt(rest, "port", 8766));
                config.setRateLimit(getInt(rest, "rateLimit", 100));
            }
        }

        // 消息转发
        Map<String, Object> forward = getMap(yaml, "messageForward");
        if (forward != null) {
            config.setForwardEnabled(getBoolean(forward, "enabled", true));
            config.setForwardPrefix(getString(forward, "prefix", "*"));
            config.setStripPrefix(getBoolean(forward, "stripPrefix", true));
            config.setIncomingFormat(getString(forward, "incomingFormat", 
                "§7[§b{platform}§7] §f{username}§7: §f{content}"));
        }

        // AI聊天
        Map<String, Object> aiChat = getMap(yaml, "aiChat");
        if (aiChat != null) {
            // 群聊
            Map<String, Object> group = getMap(aiChat, "group");
            if (group != null) {
                config.setGroupChatEnabled(getBoolean(group, "enabled", true));
                config.setGroupChatPrefix(getString(group, "prefix", "@"));
            }

            // 私聊
            Map<String, Object> priv = getMap(aiChat, "private");
            if (priv != null) {
                config.setPrivateChatEnabled(getBoolean(priv, "enabled", true));
                config.setPrivateChatPrefix(getString(priv, "prefix", "#"));
            }

        }

        // 玩家通知
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

        // 外部指令
        Map<String, Object> cmd = getMap(yaml, "commandExecution");
        if (cmd != null) {
            config.setCommandEnabled(getBoolean(cmd, "enabled", true));
            config.setCommandFilterMode(getString(cmd, "filterType", "NONE"));
            config.setCommandFilterList(getStringList(cmd, "commandList"));
        }

        // AI聊天扩展
        if (aiChat != null) {
            config.setAiResponseFormat(getString(aiChat, "responseFormat", "§7[§dAI§7] §f{content}"));
            config.setAiThinkingMessage(getString(aiChat, "thinkingMessage", "§7[§dAI§7] §e思考中..."));
            config.setAiShowThinking(getBoolean(aiChat, "showThinking", true));
            config.setAiTimeoutSeconds(getInt(aiChat, "timeout", 60));
        }

        // 日志查询
        Map<String, Object> logQuery = getMap(yaml, "logQuery");
        if (logQuery != null) {
            config.setLogQueryEnabled(getBoolean(logQuery, "enabled", true));
            config.setLogQueryMaxLines(getInt(logQuery, "maxLines", 1000));
            config.setLogQueryFile(getString(logQuery, "logFile", ""));
        }

        // 更新检查
        Map<String, Object> update = getMap(yaml, "updateCheck");
        if (update != null) {
            config.setUpdateCheckEnabled(getBoolean(update, "enabled", true));
            config.setUpdateNotifyOps(getBoolean(update, "notifyOps", true));
        }
    }

    // ===== YAML 解析器 =====
    
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
        
        // general
        java.util.LinkedHashMap<String, Object> general = new java.util.LinkedHashMap<>();
        general.put("language", config.getLanguage());
        general.put("debug", config.isDebug());
        root.put("general", general);
        
        // auth
        java.util.LinkedHashMap<String, Object> auth = new java.util.LinkedHashMap<>();
        auth.put("token", config.getToken());
        root.put("auth", auth);
        
        // server
        java.util.LinkedHashMap<String, Object> server = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, Object> ws = new java.util.LinkedHashMap<>();
        ws.put("enabled", config.isWsEnabled());
        ws.put("host", config.getWsHost());
        ws.put("port", config.getWsPort());
        ws.put("heartbeatInterval", config.getHeartbeatInterval());
        ws.put("heartbeatTimeout", config.getHeartbeatTimeout());
        server.put("websocket", ws);
        
        java.util.LinkedHashMap<String, Object> rest = new java.util.LinkedHashMap<>();
        rest.put("enabled", config.isRestEnabled());
        rest.put("host", config.getRestHost());
        rest.put("port", config.getRestPort());
        rest.put("rateLimit", config.getRateLimit());
        server.put("restapi", rest);
        root.put("server", server);

        // messageForward
        java.util.LinkedHashMap<String, Object> forward = new java.util.LinkedHashMap<>();
        forward.put("enabled", config.isForwardEnabled());
        forward.put("prefix", config.getForwardPrefix());
        forward.put("stripPrefix", config.isStripPrefix());
        forward.put("incomingFormat", config.getIncomingFormat());
        root.put("messageForward", forward);

        // aiChat
        java.util.LinkedHashMap<String, Object> aiChat = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, Object> group = new java.util.LinkedHashMap<>();
        group.put("enabled", config.isGroupChatEnabled());
        group.put("prefix", config.getGroupChatPrefix());
        aiChat.put("group", group);

        java.util.LinkedHashMap<String, Object> priv = new java.util.LinkedHashMap<>();
        priv.put("enabled", config.isPrivateChatEnabled());
        priv.put("prefix", config.getPrivateChatPrefix());
        aiChat.put("private", priv);

        aiChat.put("responseFormat", config.getAiResponseFormat());
        aiChat.put("thinkingMessage", config.getAiThinkingMessage());
        aiChat.put("showThinking", config.isAiShowThinking());
        aiChat.put("timeout", config.getAiTimeoutSeconds());
        root.put("aiChat", aiChat);

        // playerNotification
        java.util.LinkedHashMap<String, Object> notify = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, Object> join = new java.util.LinkedHashMap<>();
        join.put("enabled", config.isJoinNotifyEnabled());
        notify.put("join", join);
        java.util.LinkedHashMap<String, Object> quit = new java.util.LinkedHashMap<>();
        quit.put("enabled", config.isQuitNotifyEnabled());
        notify.put("quit", quit);
        root.put("playerNotification", notify);

        // commandExecution
        java.util.LinkedHashMap<String, Object> commandExecution = new java.util.LinkedHashMap<>();
        commandExecution.put("enabled", config.isCommandEnabled());
        commandExecution.put("filterType", config.getCommandFilterMode());
        commandExecution.put("commandList", config.getCommandFilterList());
        root.put("commandExecution", commandExecution);

        // logQuery
        java.util.LinkedHashMap<String, Object> logQuery = new java.util.LinkedHashMap<>();
        logQuery.put("enabled", config.isLogQueryEnabled());
        logQuery.put("maxLines", config.getLogQueryMaxLines());
        logQuery.put("logFile", config.getLogQueryFile());
        root.put("logQuery", logQuery);

        // updateCheck
        java.util.LinkedHashMap<String, Object> updateCheck = new java.util.LinkedHashMap<>();
        updateCheck.put("enabled", config.isUpdateCheckEnabled());
        updateCheck.put("notifyOps", config.isUpdateNotifyOps());
        root.put("updateCheck", updateCheck);
        
        return root;
    }

    private void createBasicConfig(Path file) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writeYaml(writer, buildConfigMap());
        }
    }

    // ===== 工具方法 =====

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
