package io.github.railgun19457.astrbotadapter.platform.bukkit;

import io.github.railgun19457.astrbotadapter.AstrbotAdapterPlugin;
import io.github.railgun19457.astrbotadapter.command.AstrbotCommand;
import io.github.railgun19457.astrbotadapter.command.AstrbotTabCompleter;
import io.github.railgun19457.astrbotadapter.communication.proxy.BukkitProxyClient;
import io.github.railgun19457.astrbotadapter.platform.bukkit.listener.BukkitChatListener;
import io.github.railgun19457.astrbotadapter.platform.bukkit.listener.BukkitPlayerListener;
import io.github.railgun19457.astrbotadapter.platform.bukkit.listener.BukkitProxyChatListener;
import io.github.railgun19457.astrbotadapter.platform.bukkit.listener.BukkitProxyPlayerListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Shared bootstrap helpers for Bukkit-family platforms (Paper/Folia).
 */
public final class BukkitBootstrapSupport {

    private BukkitBootstrapSupport() {
    }

    public static void registerCommands(JavaPlugin javaPlugin, AstrbotAdapterPlugin plugin) {
        PluginCommand command = javaPlugin.getCommand("astrbot");
        if (command == null) {
            javaPlugin.getLogger().warning("未找到 astrbot 命令定义，跳过命令注册");
            return;
        }

        command.setExecutor(new AstrbotCommand(plugin));
        command.setTabCompleter(new AstrbotTabCompleter());
    }

    public static void registerNormalListeners(JavaPlugin javaPlugin, AstrbotAdapterPlugin plugin) {
        javaPlugin.getServer().getPluginManager().registerEvents(
                new BukkitChatListener(plugin.getChatService(), plugin.getMessageForwardService()),
                javaPlugin
        );

        javaPlugin.getServer().getPluginManager().registerEvents(
                new BukkitPlayerListener(javaPlugin, plugin.getNotificationService()),
                javaPlugin
        );
    }

    public static BukkitProxyClient initializeProxyMode(JavaPlugin javaPlugin, AstrbotAdapterPlugin plugin) {
        BukkitProxyClient proxyClient = new BukkitProxyClient(
                javaPlugin,
                plugin.getConfigManager().getConfig(),
                plugin.getPlatformAdapter(),
                javaPlugin.getLogger()
        );
        proxyClient.initialize();

        javaPlugin.getServer().getPluginManager().registerEvents(
                new BukkitProxyChatListener(proxyClient, plugin.getConfigManager().getConfig()),
                javaPlugin
        );
        javaPlugin.getServer().getPluginManager().registerEvents(
                new BukkitProxyPlayerListener(proxyClient),
                javaPlugin
        );

        return proxyClient;
    }
}