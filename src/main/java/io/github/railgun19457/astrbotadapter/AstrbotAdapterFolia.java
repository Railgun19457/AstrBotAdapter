package io.github.railgun19457.astrbotadapter;

import io.github.railgun19457.astrbotadapter.command.AstrbotCommand;
import io.github.railgun19457.astrbotadapter.command.AstrbotTabCompleter;
import io.github.railgun19457.astrbotadapter.platform.folia.FoliaAdapter;
import io.github.railgun19457.astrbotadapter.platform.bukkit.listener.BukkitChatListener;
import io.github.railgun19457.astrbotadapter.platform.bukkit.listener.BukkitPlayerListener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * AstrbotAdapter Folia插件入口
 * Folia是Paper的区域化多线程分支
 */
public class AstrbotAdapterFolia extends JavaPlugin {

    private AstrbotAdapterPlugin pluginInstance;

    @Override
    public void onEnable() {
        // 创建插件实例包装
        pluginInstance = new FoliaPluginWrapper(this);
        pluginInstance.initialize();

        // 注册命令
        AstrbotCommand command = new AstrbotCommand(pluginInstance);
        AstrbotTabCompleter tabCompleter = new AstrbotTabCompleter();
        
        getCommand("astrbot").setExecutor(command);
        getCommand("astrbot").setTabCompleter(tabCompleter);

        // 注册监听器
        getServer().getPluginManager().registerEvents(
                new BukkitChatListener(pluginInstance.getChatService(), pluginInstance.getMessageForwardService()),
                this
        );
        getServer().getPluginManager().registerEvents(
            new BukkitPlayerListener(this, pluginInstance.getNotificationService()),
                this
        );

        getLogger().info("AstrbotAdapter (Folia) 已启用");
    }

    @Override
    public void onDisable() {
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
