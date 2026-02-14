package io.github.railgun19457.astrbotadapter;

import io.github.railgun19457.astrbotadapter.command.AstrbotCommand;
import io.github.railgun19457.astrbotadapter.command.AstrbotTabCompleter;
import io.github.railgun19457.astrbotadapter.communication.proxy.BukkitProxyClient;
import io.github.railgun19457.astrbotadapter.platform.bukkit.BukkitAdapter;
import io.github.railgun19457.astrbotadapter.platform.bukkit.listener.BukkitChatListener;
import io.github.railgun19457.astrbotadapter.platform.bukkit.listener.BukkitPlayerListener;
import io.github.railgun19457.astrbotadapter.platform.bukkit.listener.BukkitProxyChatListener;
import io.github.railgun19457.astrbotadapter.platform.bukkit.listener.BukkitProxyPlayerListener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bukkit/Paper 平台的插件入口
 */
public class AstrbotAdapterBukkit extends JavaPlugin {

    private AstrbotAdapterPlugin plugin;
    private BukkitProxyClient proxyClient;

    @Override
    public void onEnable() {
        // 创建插件包装器
        plugin = new BukkitPluginWrapper(this);
        
        // 初始化插件
        plugin.initialize();

        // 注册命令
        registerCommands();

        // Check if proxy mode is enabled
        if (plugin.getConfigManager().getConfig().isProxyModeEnabled()) {
            initializeProxyMode();
        } else {
            // Register normal listeners
            registerListeners();
        }
    }

    @Override
    public void onDisable() {
        if (proxyClient != null) {
            proxyClient.shutdown();
        }
        if (plugin != null) {
            plugin.shutdown();
        }
    }

    /**
     * Initialize proxy mode: set up Plugin Messaging Channel client.
     */
    private void initializeProxyMode() {
        proxyClient = new BukkitProxyClient(
                this,
                plugin.getConfigManager().getConfig(),
                plugin.getPlatformAdapter(),
                getLogger()
        );
        proxyClient.initialize();

        // Register proxy-mode listeners that report events to the proxy
        getServer().getPluginManager().registerEvents(
                new BukkitProxyChatListener(proxyClient, plugin.getConfigManager().getConfig()),
                this
        );
        getServer().getPluginManager().registerEvents(
                new BukkitProxyPlayerListener(proxyClient),
                this
        );

        getLogger().info("后端服务器已进入代理模式");
    }

    /**
     * 注册命令
     */
    private void registerCommands() {
        AstrbotCommand commandExecutor = new AstrbotCommand(plugin);
        AstrbotTabCompleter tabCompleter = new AstrbotTabCompleter();

        getCommand("astrbot").setExecutor(commandExecutor);
        getCommand("astrbot").setTabCompleter(tabCompleter);
    }

    /**
     * 注册监听器 (normal mode)
     */
    private void registerListeners() {
        // 聊天监听器
        getServer().getPluginManager().registerEvents(
                new BukkitChatListener(plugin.getChatService(), plugin.getMessageForwardService()),
                this
        );

        // 玩家监听器
        getServer().getPluginManager().registerEvents(
            new BukkitPlayerListener(this, plugin.getNotificationService()),
                this
        );
    }

    /**
     * Get the proxy client (only available in proxy mode).
     */
    public BukkitProxyClient getProxyClient() {
        return proxyClient;
    }

    /**
     * Bukkit 平台的插件包装器
     */
    private static class BukkitPluginWrapper extends AstrbotAdapterPlugin {

        private final AstrbotAdapterBukkit bukkitPlugin;

        public BukkitPluginWrapper(AstrbotAdapterBukkit bukkitPlugin) {
            this.bukkitPlugin = bukkitPlugin;
            this.logger = bukkitPlugin.getLogger();
            this.dataFolder = bukkitPlugin.getDataFolder();
        }

        @Override
        protected void initializePlatform() {
            this.platformAdapter = new BukkitAdapter(bukkitPlugin);
            platformAdapter.initialize();
            logger.info("Bukkit 平台适配器已初始化");
        }
    }
}
