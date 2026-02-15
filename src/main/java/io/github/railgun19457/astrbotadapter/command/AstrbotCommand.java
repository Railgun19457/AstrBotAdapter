package io.github.railgun19457.astrbotadapter.command;

import io.github.railgun19457.astrbotadapter.AstrbotAdapterPlugin;
import io.github.railgun19457.astrbotadapter.communication.UnifiedServer;
import io.github.railgun19457.astrbotadapter.communication.websocket.SessionManager;
import io.github.railgun19457.astrbotadapter.communication.websocket.WebSocketSession;
import io.github.railgun19457.astrbotadapter.core.config.PluginConfig;
import io.github.railgun19457.astrbotadapter.core.i18n.I18NManager;
import io.github.railgun19457.astrbotadapter.core.i18n.MessageKey;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Collection;

/**
 * Astrbot 命令处理器
 */
public class AstrbotCommand implements CommandExecutor {

    private final AstrbotAdapterPlugin plugin;
    private final I18NManager i18n;

    public AstrbotCommand(AstrbotAdapterPlugin plugin) {
        this.plugin = plugin;
        this.i18n = plugin.getI18NManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "help":
                showHelp(sender);
                break;
            case "reload":
                handleReload(sender);
                break;
            case "status":
                handleStatus(sender);
                break;
            case "token":
                handleToken(sender, args);
                break;
            case "connections":
                handleConnections(sender);
                break;
            default:
                sender.sendMessage(colorize("&c未知命令: " + subCommand));
                showHelp(sender);
                break;
        }

        return true;
    }

    /**
     * 显示帮助信息
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage(colorize("&6=== AstrbotAdapter 帮助 ==="));
        sender.sendMessage(colorize("&e/astrbot help &7- 显示帮助信息"));
        sender.sendMessage(colorize("&e/astrbot reload &7- 重载配置"));
        sender.sendMessage(colorize("&e/astrbot status &7- 查看插件状态"));
        sender.sendMessage(colorize("&e/astrbot token [show|regen] &7- 管理认证令牌"));
        sender.sendMessage(colorize("&e/astrbot connections &7- 查看当前连接"));
    }

    /**
     * 处理重载命令
     */
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("astrbot.reload")) {
            sender.sendMessage(colorize(i18n.getMessage(MessageKey.COMMAND_NO_PERMISSION)));
            return;
        }

        plugin.reload();
        sender.sendMessage(colorize(i18n.getMessage(MessageKey.COMMAND_RELOAD_SUCCESS)));
    }

    /**
     * 处理状态命令
     */
    private void handleStatus(CommandSender sender) {
        if (!sender.hasPermission("astrbot.status")) {
            sender.sendMessage(colorize(i18n.getMessage(MessageKey.COMMAND_NO_PERMISSION)));
            return;
        }

        PluginConfig config = plugin.getConfigManager().getConfig();
        UnifiedServer server = plugin.getUnifiedServer();

        sender.sendMessage(colorize("&6=== AstrbotAdapter 状态 ==="));

        // 统一服务器状态
        if (config.isWsEnabled() || config.isRestEnabled()) {
            boolean running = server != null && server.isRunning();
            String status = running ? "&a运行中" : "&c已停止";
            int connections = running ? server.getSessionManager().getSessionCount() : 0;
            sender.sendMessage(colorize("&e服务器: " + status + " &7(端口: " + config.getServerPort() + ")"));
            sender.sendMessage(colorize("  &7WebSocket连接数: &f" + connections));
        } else {
            sender.sendMessage(colorize("&e服务器: &7已禁用"));
        }

        // 服务状态
        sender.sendMessage(colorize("&e聊天服务: " + (plugin.getChatService() != null ? "&a已启用" : "&c已禁用")));
        sender.sendMessage(colorize("&e消息转发: " + (plugin.getMessageForwardService() != null ? "&a已启用" : "&c已禁用")));
        sender.sendMessage(colorize("&e通知服务: " + (config.isJoinNotifyEnabled() || config.isQuitNotifyEnabled() ? "&a已启用" : "&c已禁用")));

        // 平台信息
        sender.sendMessage(colorize("&e平台: &f" + plugin.getPlatformAdapter().getPlatformType().getDisplayName()));
    }

    /**
     * 处理令牌命令
     */
    private void handleToken(CommandSender sender, String[] args) {
        if (!sender.hasPermission("astrbot.token")) {
            sender.sendMessage(colorize(i18n.getMessage(MessageKey.COMMAND_NO_PERMISSION)));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(colorize("&c用法: /astrbot token <show|regen>"));
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "show":
                String token = plugin.getConfigManager().getConfig().getToken();
                if (token == null || token.isEmpty()) {
                    sender.sendMessage(colorize("&c当前没有设置认证令牌"));
                } else {
                    sender.sendMessage(colorize("&6当前令牌: &f" + token));
                    sender.sendMessage(colorize("&7请妥善保管此令牌，不要泄露给他人"));
                }
                break;
            case "regen":
            case "regenerate":
                String newToken = plugin.getAuthManager().regenerateToken();
                sender.sendMessage(colorize("&a新令牌已生成: &f" + newToken));
                sender.sendMessage(colorize("&7旧连接将在认证失败后断开"));
                break;
            default:
                sender.sendMessage(colorize("&c未知操作: " + action));
                sender.sendMessage(colorize("&c用法: /astrbot token <show|regen>"));
                break;
        }
    }

    /**
     * 处理连接列表命令
     */
    private void handleConnections(CommandSender sender) {
        if (!sender.hasPermission("astrbot.connections")) {
            sender.sendMessage(colorize(i18n.getMessage(MessageKey.COMMAND_NO_PERMISSION)));
            return;
        }

        UnifiedServer server = plugin.getUnifiedServer();
        if (server == null || !server.isRunning()) {
            sender.sendMessage(colorize("&c服务器未运行"));
            return;
        }

        SessionManager sessionManager = server.getSessionManager();
        Collection<WebSocketSession> sessions = sessionManager.getAllSessions();

        if (sessions.isEmpty()) {
            sender.sendMessage(colorize("&e当前没有活动连接"));
            return;
        }

        sender.sendMessage(colorize("&6=== 活动连接 (" + sessions.size() + ") ==="));
        for (WebSocketSession session : sessions) {
            String authStatus = session.isAuthenticated() ? "&a已认证" : "&c未认证";
            sender.sendMessage(colorize(String.format(
                    "&e%s &7- %s &7- 最后活动: %d秒前",
                    session.getSessionId(),
                    authStatus,
                    (System.currentTimeMillis() - session.getLastHeartbeat()) / 1000
            )));
        }
    }

    /**
     * 颜色代码转换
     */
    private String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
