package io.github.railgun19457.astrbotadapter.update;

import io.github.railgun19457.astrbotadapter.AstrbotAdapterPlugin;
import io.github.railgun19457.astrbotadapter.platform.common.CommonScheduler;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.logging.Logger;

/**
 * 更新通知器
 * 负责在检测到更新时通知管理员
 */
public class UpdateNotifier implements Listener {

    private static final String GITHUB_OWNER = "Railgun19457";
    private static final String GITHUB_REPO = "AstrbotAdapter";
    private static final long CHECK_INTERVAL = 6 * 60 * 60 * 20L; // 6小时（以 ticks 计）

    private final AstrbotAdapterPlugin plugin;
    private final UpdateChecker updateChecker;
    private final Logger logger;

    private int checkTaskId = -1;

    public UpdateNotifier(AstrbotAdapterPlugin plugin, String currentVersion) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.updateChecker = new GithubReleaseChecker(
                GITHUB_OWNER,
                GITHUB_REPO,
                currentVersion,
                logger
        );
    }

    /**
     * 启动更新检查
     */
    public void start() {
        CommonScheduler scheduler = plugin.getPlatformAdapter().getScheduler();

        // 延迟 1 分钟后首次检查
        scheduler.runLaterAsync(() -> {
            logger.info("正在检查更新...");
            checkForUpdate();
        }, 20 * 60L);

        // 定期检查
        checkTaskId = scheduler.runTimerAsync(this::checkForUpdate, CHECK_INTERVAL, CHECK_INTERVAL);
    }

    /**
     * 停止更新检查
     */
    public void stop() {
        if (checkTaskId != -1) {
            plugin.getPlatformAdapter().getScheduler().cancelTask(checkTaskId);
            checkTaskId = -1;
        }
    }

    /**
     * 检查更新
     */
    private void checkForUpdate() {
        updateChecker.checkForUpdateAsync(versionInfo -> {
            if (versionInfo == null) {
                return;
            }

            if (updateChecker.isUpdateAvailable()) {
                logger.info("发现新版本: " + versionInfo.getVersion());
                logger.info("下载地址: " + versionInfo.getDownloadUrl());

                // 通知在线管理员
                notifyOnlineAdmins(versionInfo);
            } else {
                logger.info("当前已是最新版本");
            }
        });
    }

    /**
     * 通知在线管理员
     */
    private void notifyOnlineAdmins(VersionInfo versionInfo) {
        plugin.getPlatformAdapter().getScheduler().runSync(() -> {
            String message = formatUpdateMessage(versionInfo);
            plugin.getPlatformAdapter().getOnlinePlayers().stream()
                    .filter(player -> player.hasPermission("astrbot.admin"))
                    .forEach(player -> player.sendMessage(message));
        });
    }

    /**
     * 玩家加入时通知
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("astrbot.admin")) {
            return;
        }

        if (!updateChecker.isUpdateAvailable()) {
            return;
        }

        VersionInfo versionInfo = updateChecker.getLatestVersion();
        if (versionInfo == null) {
            return;
        }

        // 延迟发送消息
        plugin.getPlatformAdapter().getScheduler().runLater(() -> {
            if (player.isOnline()) {
                player.sendMessage(formatUpdateMessage(versionInfo));
            }
        }, 60L); // 3秒后
    }

    /**
     * 格式化更新消息
     */
    private String formatUpdateMessage(VersionInfo versionInfo) {
        return ChatColor.translateAlternateColorCodes('&', String.format(
                "&6[AstrbotAdapter] &a发现新版本: &f%s\n" +
                "&6[AstrbotAdapter] &7当前版本: &f%s\n" +
                "&6[AstrbotAdapter] &7下载: &b%s",
                versionInfo.getVersion(),
                updateChecker.getCurrentVersion(),
                versionInfo.getDownloadUrl()
        ));
    }

    /**
     * 获取更新检查器
     */
    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }
}
