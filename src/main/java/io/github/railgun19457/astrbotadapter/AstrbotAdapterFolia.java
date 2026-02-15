package io.github.railgun19457.astrbotadapter;

import io.github.railgun19457.astrbotadapter.communication.proxy.BukkitProxyClient;
import io.github.railgun19457.astrbotadapter.platform.bukkit.BukkitBootstrapSupport;
import io.github.railgun19457.astrbotadapter.platform.folia.FoliaAdapter;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * AstrbotAdapter Folia插件入口
 * Folia是Paper的区域化多线程分支
 */
public class AstrbotAdapterFolia extends JavaPlugin {

    private AstrbotAdapterPlugin pluginInstance;
    private BukkitProxyClient proxyClient;

    @Override
    public void onEnable() {
        // 创建插件实例包装
        pluginInstance = new FoliaPluginWrapper(this);
        pluginInstance.initialize();

        // 注册命令
        BukkitBootstrapSupport.registerCommands(this, pluginInstance);

        // Check if proxy mode is enabled
        if (pluginInstance.getConfigManager().getConfig().isProxyModeEnabled()) {
            proxyClient = BukkitBootstrapSupport.initializeProxyMode(this, pluginInstance);
            getLogger().info("后端服务器已进入代理模式 (Folia)");
        } else {
            // Register normal listeners
            BukkitBootstrapSupport.registerNormalListeners(this, pluginInstance);
        }

        getLogger().info("AstrbotAdapter (Folia) 已启用");
    }

    @Override
    public void onDisable() {
        if (proxyClient != null) {
            proxyClient.shutdown();
        }
        if (pluginInstance != null) {
            pluginInstance.shutdown();
        }
        getLogger().info("AstrbotAdapter (Folia) 已禁用");
    }

    /**
     * Folia插件包装类
     */
    private static class FoliaPluginWrapper extends AstrbotAdapterPlugin {
        private final JavaPlugin javaPlugin;

        public FoliaPluginWrapper(JavaPlugin javaPlugin) {
            this.javaPlugin = javaPlugin;
            this.logger = javaPlugin.getLogger();
            this.dataFolder = javaPlugin.getDataFolder();
        }

        @Override
        protected void initializePlatform() {
            platformAdapter = new FoliaAdapter(javaPlugin);
            platformAdapter.initialize();
            logger.info("Folia平台适配器已初始化");
        }
    }
}
