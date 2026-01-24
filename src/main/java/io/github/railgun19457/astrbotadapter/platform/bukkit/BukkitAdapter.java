package io.github.railgun19457.astrbotadapter.platform.bukkit;

import io.github.railgun19457.astrbotadapter.platform.PlatformAdapter;
import io.github.railgun19457.astrbotadapter.platform.PlatformDetector;
import io.github.railgun19457.astrbotadapter.platform.PlatformType;
import io.github.railgun19457.astrbotadapter.platform.common.CommonPlayer;
import io.github.railgun19457.astrbotadapter.platform.common.CommonScheduler;
import io.github.railgun19457.astrbotadapter.platform.common.CommonServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Bukkit/Paper平台适配器
 */
public class BukkitAdapter implements PlatformAdapter {

    private final JavaPlugin plugin;
    private final PlatformType platformType;
    private final CommonServer server;
    private final CommonScheduler scheduler;
    private final long startTime;

    public BukkitAdapter(JavaPlugin plugin) {
        this.plugin = plugin;
        this.platformType = PlatformDetector.detect();
        this.server = new BukkitServer(Bukkit.getServer());
        this.scheduler = new BukkitSchedulerWrapper(plugin);
        this.startTime = System.currentTimeMillis();
    }

    @Override
    public PlatformType getPlatformType() {
        return platformType;
    }

    @Override
    public String getServerVersion() {
        return Bukkit.getVersion();
    }

    @Override
    public String getServerMotd() {
        return Bukkit.getMotd();
    }

    @Override
    public long getServerUptime() {
        return System.currentTimeMillis() - startTime;
    }

    @Override
    public String getServerName() {
        return Bukkit.getServer().getName();
    }

    @Override
    public CommonServer getServer() {
        return server;
    }

    @Override
    public Collection<CommonPlayer> getOnlinePlayers() {
        return Bukkit.getOnlinePlayers().stream()
                .map(BukkitPlayer::new)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<CommonPlayer> getPlayer(String name) {
        Player player = Bukkit.getPlayer(name);
        return player != null ? Optional.of(new BukkitPlayer(player)) : Optional.empty();
    }

    @Override
    public Optional<CommonPlayer> getPlayer(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null ? Optional.of(new BukkitPlayer(player)) : Optional.empty();
    }

    @Override
    public int getOnlinePlayerCount() {
        return Bukkit.getOnlinePlayers().size();
    }

    @Override
    public int getMaxPlayers() {
        return Bukkit.getMaxPlayers();
    }

    @Override
    public void broadcastMessage(String message) {
        Bukkit.broadcastMessage(message);
    }

    @Override
    public void sendMessage(CommonPlayer player, String message) {
        if (player instanceof BukkitPlayer) {
            ((BukkitPlayer) player).getBukkitPlayer().sendMessage(message);
        }
    }

    @Override
    public void sendConsoleMessage(String message) {
        Bukkit.getConsoleSender().sendMessage(message);
    }

    @Override
    public boolean executeCommand(String command) {
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    @Override
    public boolean executeCommand(CommonPlayer player, String command) {
        if (player instanceof BukkitPlayer) {
            return Bukkit.dispatchCommand(((BukkitPlayer) player).getBukkitPlayer(), command);
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
            plugin.getLogger().warning("读取日志文件失败: " + e.getMessage());
        }

        return logs;
    }

    @Override
    public List<String> getLogsByTimeRange(long startTime, long endTime) {
        // 简化实现：返回最近的日志
        // 实际生产环境中可以解析日志时间戳进行过滤
        return getRecentLogs(500);
    }

    @Override
    public void initialize() {
        plugin.getLogger().info("Bukkit适配器已初始化");
    }

    @Override
    public void shutdown() {
        scheduler.cancelAll();
        plugin.getLogger().info("Bukkit适配器已关闭");
    }

    @Override
    public void registerListeners() {
        // 监听器在插件主类中注册
    }

    @Override
    public void unregisterListeners() {
        // 监听器在插件禁用时自动注销
    }

    /**
     * 获取插件实例
     */
    public JavaPlugin getPlugin() {
        return plugin;
    }
}
