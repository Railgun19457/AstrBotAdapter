package io.github.railgun19457.astrbotadapter.platform;

import java.util.logging.Logger;

/**
 * 平台检测器
 * 自动检测当前运行的服务器平台
 */
public class PlatformDetector {

    private static final Logger LOGGER = Logger.getLogger(PlatformDetector.class.getName());

    /**
     * 检测当前平台类型
     * @return 检测到的平台类型
     */
    public static PlatformType detect() {
        // 检测Folia
        if (isFolia()) {
            LOGGER.info("检测到平台: Folia");
            return PlatformType.FOLIA;
        }

        // 检测Paper
        if (isPaper()) {
            LOGGER.info("检测到平台: Paper");
            return PlatformType.PAPER;
        }

        // 检测Velocity
        if (isVelocity()) {
            LOGGER.info("检测到平台: Velocity");
            return PlatformType.VELOCITY;
        }

        // 检测Bukkit/Spigot
        if (isBukkit()) {
            LOGGER.info("检测到平台: Bukkit/Spigot");
            return PlatformType.BUKKIT;
        }

        LOGGER.warning("无法检测到已知平台");
        return PlatformType.UNKNOWN;
    }

    /**
     * 检测是否为Folia
     */
    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 检测是否为Paper
     */
    public static boolean isPaper() {
        try {
            Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 检测是否为Velocity
     */
    public static boolean isVelocity() {
        try {
            Class.forName("com.velocitypowered.api.proxy.ProxyServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 检测是否为Bukkit/Spigot
     */
    public static boolean isBukkit() {
        try {
            Class.forName("org.bukkit.Bukkit");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 获取Minecraft版本
     * @return 版本字符串，无法获取时返回"Unknown"
     */
    public static String getMinecraftVersion() {
        try {
            // 尝试从Bukkit获取
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
            Object server = bukkitClass.getMethod("getServer").invoke(null);
            String version = (String) server.getClass().getMethod("getVersion").invoke(server);
            
            // 从版本字符串中提取MC版本 (格式通常为 "git-Paper-xxx (MC: 1.20.4)")
            int mcIndex = version.indexOf("MC:");
            if (mcIndex != -1) {
                int start = mcIndex + 4;
                int end = version.indexOf(")", start);
                if (end != -1) {
                    return version.substring(start, end).trim();
                }
            }
            
            return version;
        } catch (Exception e) {
            return "Unknown";
        }
    }
}
