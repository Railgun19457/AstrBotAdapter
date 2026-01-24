package io.github.railgun19457.astrbotadapter.platform.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import io.github.railgun19457.astrbotadapter.platform.PlatformAdapter;
import io.github.railgun19457.astrbotadapter.platform.PlatformType;
import io.github.railgun19457.astrbotadapter.platform.common.CommonPlayer;
import io.github.railgun19457.astrbotadapter.platform.common.CommonScheduler;
import io.github.railgun19457.astrbotadapter.platform.common.CommonServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Velocity平台适配器
 */
public class VelocityAdapter implements PlatformAdapter {

    private final Object plugin;
    private final ProxyServer proxy;
    private final Logger logger;
    private final CommonServer server;
    private final CommonScheduler scheduler;
    private final long startTime;

    public VelocityAdapter(Object plugin, ProxyServer proxy, Logger logger) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.logger = logger;
        this.server = new VelocityServer(proxy);
        this.scheduler = new VelocityScheduler(plugin, proxy);
        this.startTime = System.currentTimeMillis();
    }

    @Override
    public PlatformType getPlatformType() {
        return PlatformType.VELOCITY;
    }

    @Override
    public String getServerVersion() {
        return proxy.getVersion().getVersion();
    }

    @Override
    public String getServerMotd() {
        return proxy.getConfiguration().getMotd().toString();
    }

    @Override
    public long getServerUptime() {
        return System.currentTimeMillis() - startTime;
    }

    @Override
    public String getServerName() {
        return "Velocity";
    }

    @Override
    public CommonServer getServer() {
        return server;
    }

    @Override
    public Collection<CommonPlayer> getOnlinePlayers() {
        return proxy.getAllPlayers().stream()
                .map(VelocityPlayer::new)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<CommonPlayer> getPlayer(String name) {
        return proxy.getPlayer(name).map(VelocityPlayer::new);
    }

    @Override
    public Optional<CommonPlayer> getPlayer(UUID uuid) {
        return proxy.getPlayer(uuid).map(VelocityPlayer::new);
    }

    @Override
    public int getOnlinePlayerCount() {
        return proxy.getPlayerCount();
    }

    @Override
    public int getMaxPlayers() {
        return proxy.getConfiguration().getShowMaxPlayers();
    }

    @Override
    public void broadcastMessage(String message) {
        Component component = Component.text(message);
        for (Player player : proxy.getAllPlayers()) {
            player.sendMessage(component);
        }
    }

    @Override
    public void sendMessage(CommonPlayer player, String message) {
        if (player instanceof VelocityPlayer) {
            ((VelocityPlayer) player).getVelocityPlayer().sendMessage(Component.text(message));
        }
    }

    @Override
    public void sendConsoleMessage(String message) {
        logger.info(message);
    }

    @Override
    public boolean executeCommand(String command) {
        // Velocity代理不能直接执行后端服务器命令
        // 只能执行代理命令
        proxy.getCommandManager().executeAsync(proxy.getConsoleCommandSource(), command);
        return true;
    }

    @Override
    public boolean executeCommand(CommonPlayer player, String command) {
        if (player instanceof VelocityPlayer) {
            Player velocityPlayer = ((VelocityPlayer) player).getVelocityPlayer();
            proxy.getCommandManager().executeAsync(velocityPlayer, command);
            return true;
        }
        return false;
    }

    @Override
    public CommonScheduler getScheduler() {
        return scheduler;
    }

    @Override
    public List<String> getRecentLogs(int lines) {
        List<String> logs = new ArrayList<>();
        Path logFile = Path.of("logs", "latest.log");
        
        if (!Files.exists(logFile)) {
            return logs;
        }

        try {
            List<String> allLines = Files.readAllLines(logFile);
            int start = Math.max(0, allLines.size() - lines);
            logs.addAll(allLines.subList(start, allLines.size()));
        } catch (Exception e) {
            logger.warn("读取日志文件失败: {}", e.getMessage());
        }

        return logs;
    }

    @Override
    public List<String> getLogsByTimeRange(long startTime, long endTime) {
        return getRecentLogs(500);
    }

    @Override
    public void initialize() {
        logger.info("Velocity适配器已初始化");
    }

    @Override
    public void shutdown() {
        scheduler.cancelAll();
        logger.info("Velocity适配器已关闭");
    }

    @Override
    public void registerListeners() {
        // 监听器在插件主类中注册
    }

    @Override
    public void unregisterListeners() {
        // Velocity事件监听器需要手动注销
    }

    /**
     * 获取ProxyServer实例
     */
    public ProxyServer getProxy() {
        return proxy;
    }

    /**
     * 获取插件实例
     */
    public Object getPlugin() {
        return plugin;
    }
}
