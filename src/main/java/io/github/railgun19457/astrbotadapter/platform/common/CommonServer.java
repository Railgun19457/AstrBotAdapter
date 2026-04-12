package io.github.railgun19457.astrbotadapter.platform.common;

/**
 * 通用服务器接口
 * 提供服务器相关信息
 */
public interface CommonServer {

    /**
     * 获取服务器名称
     */
    String getName();

    /**
     * 获取服务器MOTD
     */
    String getMotd();

    /**
     * 获取服务器版本
     */
    String getVersion();

    /**
     * 获取最大玩家数
     */
    int getMaxPlayers();

    /**
     * 获取在线玩家数
     */
    int getOnlinePlayerCount();

    /**
     * 获取服务器TPS（每秒tick数）
     * @return TPS数组（1分钟、5分钟、15分钟平均值），代理端返回null
     */
    default double[] getTps() {
        return null;
    }

    /**
     * 获取服务器MSPT（毫秒每tick）
     * @return MSPT，平台不支持时返回null
     */
    default Double getMspt() {
        return null;
    }

    /**
     * 获取服务器运行时间（毫秒）
     */
    long getUptime();

    /**
     * 获取服务器端口
     */
    int getPort();

    /**
     * 获取服务器IP
     */
    String getIp();
}
