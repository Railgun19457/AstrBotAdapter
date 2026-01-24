package io.github.railgun19457.astrbotadapter;

import io.github.railgun19457.astrbotadapter.command.AstrbotCommand;
import io.github.railgun19457.astrbotadapter.command.AstrbotTabCompleter;
import io.github.railgun19457.astrbotadapter.platform.bukkit.BukkitAdapter;
import io.github.railgun19457.astrbotadapter.platform.bukkit.listener.BukkitChatListener;
import io.github.railgun19457.astrbotadapter.platform.bukkit.listener.BukkitPlayerListener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bukkit/Paper 平台的插件入口
 */
public class AstrbotAdapterBukkit extends JavaPlugin {

    private AstrbotAdapterPlugin plugin;

    @Override
    public void onEnable() {
        // 创建插件包装器
        plugin = new BukkitPluginWrapper(this);
        
        // 初始化插件
        plugin.initialize();

        // 注册命令
        registerCommands();

        // 注册监听器
        registerListeners();
    }

    @Override
    public void onDisable() {
        if (plugin != null) {
            plugin.shutdown();
        }
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
     * 注册监听器
     */
    private void registerListeners() {
        // 聊天监听器
        getServer().getPluginManager().registerEvents(
                new BukkitChatListener(plugin.getChatService(), plugin.getMessageForwardService()),
                this
        );

        // 玩家监听器
        getServer().getPluginManager().registerEvents(
                new BukkitPlayerListener(plugin.getNotificationService()),
                this
        );
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
