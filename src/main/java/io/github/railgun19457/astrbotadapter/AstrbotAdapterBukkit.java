package io.github.railgun19457.astrbotadapter;

import io.github.railgun19457.astrbotadapter.communication.proxy.BukkitProxyClient;
import io.github.railgun19457.astrbotadapter.platform.bukkit.BukkitAdapter;
import io.github.railgun19457.astrbotadapter.platform.bukkit.BukkitBootstrapSupport;
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
        BukkitBootstrapSupport.registerCommands(this, plugin);

        // Check if proxy mode is enabled
        if (plugin.getConfigManager().getConfig().isProxyModeEnabled()) {
            proxyClient = BukkitBootstrapSupport.initializeProxyMode(this, plugin);
            getLogger().info("后端服务器已进入代理模式");
        } else {
            // Register normal listeners
            BukkitBootstrapSupport.registerNormalListeners(this, plugin);
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
