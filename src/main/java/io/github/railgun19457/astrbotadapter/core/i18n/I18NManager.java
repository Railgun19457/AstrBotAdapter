package io.github.railgun19457.astrbotadapter.core.i18n;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 国际化管理器
 * 负责加载语言文件和获取本地化消息
 */
public class I18NManager {

    private final Path langFolder;
    private final Logger logger;
    private final Map<String, String> messages;
    private String currentLocale;

    public I18NManager(Path dataFolder, Logger logger) {
        this.langFolder = dataFolder.resolve("lang");
        this.logger = logger;
        this.messages = new HashMap<>();
        this.currentLocale = "zh_CN";
    }

    /**
     * 加载指定语言的消息文件
     * @param locale 语言代码 (zh_CN, en_US等)
     * @return 是否加载成功
     */
    public boolean loadLanguage(String locale) {
        this.currentLocale = locale;
        messages.clear();

        // 确保语言文件夹存在
        try {
            Files.createDirectories(langFolder);
        } catch (IOException e) {
            logger.severe("创建语言文件夹失败: " + e.getMessage());
            return false;
        }

        // 保存默认语言文件
        saveDefaultLanguageFiles();

        // 加载语言文件
        Path langFile = langFolder.resolve(locale + ".yml");
        if (!Files.exists(langFile)) {
            logger.warning("语言文件不存在: " + locale + ".yml, 尝试加载默认语言");
            langFile = langFolder.resolve("zh_CN.yml");
            if (!Files.exists(langFile)) {
                logger.severe("默认语言文件不存在，使用内置消息");
                loadBuiltinMessages();
                return false;
            }
        }

        try {
            loadMessagesFromFile(langFile);
            logger.info("已加载语言: " + locale);
            return true;
        } catch (IOException e) {
            logger.severe("加载语言文件失败: " + e.getMessage());
            loadBuiltinMessages();
            return false;
        }
    }

    /**
     * 重新加载当前语言
     */
    public boolean reload() {
        return loadLanguage(currentLocale);
    }

    /**
     * 获取消息
     * @param key 消息键
     * @return 本地化消息
     */
    public String getMessage(MessageKey key) {
        return messages.getOrDefault(key.getKey(), key.getKey());
    }

    /**
     * 获取消息并替换占位符
     * @param key 消息键
     * @param replacements 替换参数 (key1, value1, key2, value2, ...)
     * @return 处理后的消息
     */
    public String getMessage(MessageKey key, Object... replacements) {
        String message = getMessage(key);
        
        if (replacements.length >= 2) {
            for (int i = 0; i < replacements.length - 1; i += 2) {
                String placeholder = String.valueOf(replacements[i]);
                String value = String.valueOf(replacements[i + 1]);
                message = message.replace("{" + placeholder + "}", value);
            }
        }
        
        return message;
    }

    /**
     * 格式化消息（使用位置参数）
     * @param key 消息键
     * @param args 位置参数
     * @return 格式化后的消息
     */
    public String format(MessageKey key, Object... args) {
        String message = getMessage(key);
        
        for (int i = 0; i < args.length; i++) {
            message = message.replace("{" + i + "}", String.valueOf(args[i]));
        }
        
        return message;
    }

    /**
     * 获取当前语言
     */
    public String getCurrentLocale() {
        return currentLocale;
    }

    /**
     * 保存默认语言文件
     */
    private void saveDefaultLanguageFiles() {
        saveLanguageResource("zh_CN.yml");
        saveLanguageResource("en_US.yml");
    }

    private void saveLanguageResource(String filename) {
        Path targetFile = langFolder.resolve(filename);
        if (Files.exists(targetFile)) {
            return;
        }

        try (InputStream in = getClass().getClassLoader().getResourceAsStream("lang/" + filename)) {
            if (in != null) {
                Files.copy(in, targetFile);
            } else {
                // 如果资源不存在，创建默认内容
                if (filename.equals("zh_CN.yml")) {
                    createDefaultChineseFile(targetFile);
                } else if (filename.equals("en_US.yml")) {
                    createDefaultEnglishFile(targetFile);
                }
            }
        } catch (IOException e) {
            logger.warning("保存语言文件失败: " + filename);
        }
    }

    private void loadMessagesFromFile(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            String currentSection = "";
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                
                // 跳过空行和注释
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int colonIndex = line.indexOf(':');
                if (colonIndex <= 0) {
                    continue;
                }

                String key = line.substring(0, colonIndex).trim();
                String value = colonIndex < line.length() - 1 ? 
                    line.substring(colonIndex + 1).trim() : "";

                // 检查是否是节点
                if (value.isEmpty()) {
                    currentSection = key + ".";
                    continue;
                }

                // 移除引号
                if ((value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }

                // 处理转义字符
                value = value.replace("\\n", "\n");

                // 存储消息（考虑嵌套）
                String fullKey = key.contains(".") ? key : currentSection + key;
                messages.put(fullKey, value);
            }
        }
    }

    private void loadBuiltinMessages() {
        // 加载内置中文消息
        messages.put("plugin.enabled", "§a[AstrbotAdaptor] 插件已启用");
        messages.put("plugin.disabled", "§c[AstrbotAdaptor] 插件已禁用");
        messages.put("plugin.reloaded", "§a[AstrbotAdaptor] 配置已重载");
        messages.put("plugin.reload-failed", "§c[AstrbotAdaptor] 配置重载失败");
        
        messages.put("auth.token-generated", "§e[AstrbotAdaptor] 已生成新的认证Token");
        messages.put("auth.token-invalid", "§c认证失败: Token无效");
        messages.put("auth.token-missing", "§c认证失败: 缺少Token");
        
        messages.put("websocket.server-started", "§a[AstrbotAdaptor] WebSocket服务器已启动 - {host}:{port}");
        messages.put("websocket.server-stopped", "§e[AstrbotAdaptor] WebSocket服务器已停止");
        messages.put("websocket.client-connected", "§a[AstrbotAdaptor] 客户端已连接: {address}");
        messages.put("websocket.client-disconnected", "§e[AstrbotAdaptor] 客户端已断开: {address}");
        messages.put("websocket.status-connected", "§a已连接");
        messages.put("websocket.status-disconnected", "§c未连接");
        messages.put("websocket.status-waiting", "§e等待连接中...");
        
        messages.put("rest.server-started", "§a[AstrbotAdaptor] REST API服务器已启动 - {host}:{port}");
        messages.put("rest.server-stopped", "§e[AstrbotAdaptor] REST API服务器已停止");
        
        messages.put("command.no-permission", "§c你没有权限执行此命令");
        messages.put("command.usage", "§e用法: {usage}");
        messages.put("command.executed", "§a指令已执行: {command}");
        
        messages.put("status.header", "§6===== AstrbotAdaptor 状态 =====");
        messages.put("status.ws-clients", "§7WebSocket连接数: §f{count}");
        messages.put("status.uptime", "§7运行时间: §f{time}");
        
        messages.put("error.internal", "§c内部错误: {message}");
        messages.put("error.network", "§c网络错误: {message}");
    }

    private void createDefaultChineseFile(Path file) throws IOException {
        String content = """
                # AstrbotAdaptor 语言文件 - 简体中文
                
                plugin:
                  enabled: "§a[AstrbotAdaptor] 插件已启用"
                  disabled: "§c[AstrbotAdaptor] 插件已禁用"
                  reloaded: "§a[AstrbotAdaptor] 配置已重载"
                  reload-failed: "§c[AstrbotAdaptor] 配置重载失败"
                
                auth:
                  token-generated: "§e[AstrbotAdaptor] 已生成新的认证Token"
                  token-invalid: "§c认证失败: Token无效"
                  token-missing: "§c认证失败: 缺少Token"
                  success: "§a认证成功"
                  failed: "§c认证失败"
                
                websocket:
                  server-started: "§a[AstrbotAdaptor] WebSocket服务器已启动 - {host}:{port}"
                  server-stopped: "§e[AstrbotAdaptor] WebSocket服务器已停止"
                  server-error: "§c[AstrbotAdaptor] WebSocket服务器错误: {error}"
                  client-connected: "§a[AstrbotAdaptor] 客户端已连接: {address}"
                  client-disconnected: "§e[AstrbotAdaptor] 客户端已断开: {address}"
                  status-connected: "§a已连接"
                  status-disconnected: "§c未连接"
                  status-waiting: "§e等待连接中..."
                
                rest:
                  server-started: "§a[AstrbotAdaptor] REST API服务器已启动 - {host}:{port}"
                  server-stopped: "§e[AstrbotAdaptor] REST API服务器已停止"
                  server-error: "§c[AstrbotAdaptor] REST API服务器错误: {error}"
                  rate-limited: "§c请求过于频繁，请稍后再试"
                
                forward:
                  enabled: "§a消息转发已启用"
                  disabled: "§e消息转发已禁用"
                  success: "§a消息已转发"
                  failed: "§c消息转发失败"
                
                chat:
                  group-enabled: "§a群聊AI已启用"
                  group-disabled: "§e群聊AI已禁用"
                  private-enabled: "§a私聊AI已启用"
                  private-disabled: "§e私聊AI已禁用"
                  request-sent: "§7正在思考..."
                  response-received: "§a收到AI回复"
                  error: "§cAI聊天错误: {error}"
                
                command:
                  no-permission: "§c你没有权限执行此命令"
                  player-only: "§c此命令只能由玩家执行"
                  console-only: "§c此命令只能在控制台执行"
                  usage: "§e用法: {usage}"
                  unknown: "§c未知子命令: {command}"
                  executed: "§a指令已执行: {command}"
                  execute-failed: "§c指令执行失败: {command}"
                  filtered: "§c指令被过滤: {command}"
                
                status:
                  header: "§6===== AstrbotAdaptor 状态 ====="
                  ws-enabled: "§7WebSocket: §a已启用"
                  ws-disabled: "§7WebSocket: §c已禁用"
                  ws-clients: "§7WebSocket连接数: §f{count}"
                  rest-enabled: "§7REST API: §a已启用"
                  rest-disabled: "§7REST API: §c已禁用"
                  uptime: "§7运行时间: §f{time}"
                
                notify:
                  player-join: "§a玩家 {player} 加入了服务器"
                  player-quit: "§e玩家 {player} 离开了服务器"
                  sent: "§a通知已发送"
                  failed: "§c通知发送失败"
                
                error:
                  config-load: "§c配置加载失败: {error}"
                  config-save: "§c配置保存失败: {error}"
                  internal: "§c内部错误: {message}"
                  network: "§c网络错误: {message}"
                  timeout: "§c请求超时"
                  invalid-request: "§c无效请求: {message}"
                  player-not-found: "§c玩家不存在: {player}"
                  feature-disabled: "§c此功能已禁用"
                
                update:
                  checking: "§7正在检查更新..."
                  available: "§a发现新版本: {version}，当前版本: {current}"
                  latest: "§a当前已是最新版本"
                  check-failed: "§e更新检查失败"
                """;

        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private void createDefaultEnglishFile(Path file) throws IOException {
        String content = """
                # AstrbotAdaptor Language File - English
                
                plugin:
                  enabled: "§a[AstrbotAdaptor] Plugin enabled"
                  disabled: "§c[AstrbotAdaptor] Plugin disabled"
                  reloaded: "§a[AstrbotAdaptor] Configuration reloaded"
                  reload-failed: "§c[AstrbotAdaptor] Configuration reload failed"
                
                auth:
                  token-generated: "§e[AstrbotAdaptor] New authentication token generated"
                  token-invalid: "§cAuthentication failed: Invalid token"
                  token-missing: "§cAuthentication failed: Token missing"
                  success: "§aAuthentication successful"
                  failed: "§cAuthentication failed"
                
                websocket:
                  server-started: "§a[AstrbotAdaptor] WebSocket server started - {host}:{port}"
                  server-stopped: "§e[AstrbotAdaptor] WebSocket server stopped"
                  server-error: "§c[AstrbotAdaptor] WebSocket server error: {error}"
                  client-connected: "§a[AstrbotAdaptor] Client connected: {address}"
                  client-disconnected: "§e[AstrbotAdaptor] Client disconnected: {address}"
                  status-connected: "§aConnected"
                  status-disconnected: "§cDisconnected"
                  status-waiting: "§eWaiting for connection..."
                
                rest:
                  server-started: "§a[AstrbotAdaptor] REST API server started - {host}:{port}"
                  server-stopped: "§e[AstrbotAdaptor] REST API server stopped"
                  server-error: "§c[AstrbotAdaptor] REST API server error: {error}"
                  rate-limited: "§cToo many requests, please try again later"
                
                forward:
                  enabled: "§aMessage forwarding enabled"
                  disabled: "§eMessage forwarding disabled"
                  success: "§aMessage forwarded"
                  failed: "§cMessage forwarding failed"
                
                chat:
                  group-enabled: "§aGroup AI chat enabled"
                  group-disabled: "§eGroup AI chat disabled"
                  private-enabled: "§aPrivate AI chat enabled"
                  private-disabled: "§ePrivate AI chat disabled"
                  request-sent: "§7Thinking..."
                  response-received: "§aReceived AI response"
                  error: "§cAI chat error: {error}"
                
                command:
                  no-permission: "§cYou don't have permission to execute this command"
                  player-only: "§cThis command can only be executed by players"
                  console-only: "§cThis command can only be executed from console"
                  usage: "§eUsage: {usage}"
                  unknown: "§cUnknown subcommand: {command}"
                  executed: "§aCommand executed: {command}"
                  execute-failed: "§cCommand execution failed: {command}"
                  filtered: "§cCommand filtered: {command}"
                
                status:
                  header: "§6===== AstrbotAdaptor Status ====="
                  ws-enabled: "§7WebSocket: §aEnabled"
                  ws-disabled: "§7WebSocket: §cDisabled"
                  ws-clients: "§7WebSocket connections: §f{count}"
                  rest-enabled: "§7REST API: §aEnabled"
                  rest-disabled: "§7REST API: §cDisabled"
                  uptime: "§7Uptime: §f{time}"
                
                notify:
                  player-join: "§aPlayer {player} joined the server"
                  player-quit: "§ePlayer {player} left the server"
                  sent: "§aNotification sent"
                  failed: "§cNotification failed"
                
                error:
                  config-load: "§cFailed to load config: {error}"
                  config-save: "§cFailed to save config: {error}"
                  internal: "§cInternal error: {message}"
                  network: "§cNetwork error: {message}"
                  timeout: "§cRequest timeout"
                  invalid-request: "§cInvalid request: {message}"
                  player-not-found: "§cPlayer not found: {player}"
                  feature-disabled: "§cThis feature is disabled"
                
                update:
                  checking: "§7Checking for updates..."
                  available: "§aNew version available: {version}, current: {current}"
                  latest: "§aYou are using the latest version"
                  check-failed: "§eUpdate check failed"
                """;

        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
