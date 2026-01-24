package io.github.railgun19457.astrbotadapter.platform;

import io.github.railgun19457.astrbotadapter.platform.common.CommonPlayer;
import io.github.railgun19457.astrbotadapter.platform.common.CommonScheduler;
import io.github.railgun19457.astrbotadapter.platform.common.CommonServer;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 平台适配器接口
 * 定义不同服务器平台需要实现的统一接口
 */
public interface PlatformAdapter {

    // ===== 平台信息 =====

    /**
     * 获取平台类型
     */
    PlatformType getPlatformType();

    /**
     * 获取服务器版本
     */
    String getServerVersion();

    /**
     * 获取服务器MOTD
     */
    String getServerMotd();

    /**
     * 获取服务器运行时间（毫秒）
     */
    long getServerUptime();

    /**
     * 获取服务器名称
     */
    String getServerName();

    /**
     * 获取服务器对象
     */
    CommonServer getServer();

    // ===== 玩家操作 =====

    /**
     * 获取所有在线玩家
     */
    Collection<CommonPlayer> getOnlinePlayers();

    /**
     * 通过名称获取玩家
     * @param name 玩家名称
     * @return 玩家对象（可能不存在）
     */
    Optional<CommonPlayer> getPlayer(String name);

    /**
     * 通过UUID获取玩家
     * @param uuid 玩家UUID
     * @return 玩家对象（可能不存在）
     */
    Optional<CommonPlayer> getPlayer(UUID uuid);

    /**
     * 获取在线玩家数量
     */
    int getOnlinePlayerCount();

    /**
     * 获取最大玩家数量
     */
    int getMaxPlayers();

    // ===== 消息发送 =====

    /**
     * 向所有玩家广播消息
     * @param message 消息内容
     */
    void broadcastMessage(String message);

    /**
     * 向指定玩家发送消息
     * @param player 玩家对象
     * @param message 消息内容
     */
    void sendMessage(CommonPlayer player, String message);

    /**
     * 在控制台输出消息
     * @param message 消息内容
     */
    void sendConsoleMessage(String message);

    // ===== 指令执行 =====

    /**
     * 以控制台身份执行指令
     * @param command 指令（不含/）
     * @return 是否执行成功
     */
    boolean executeCommand(String command);

    /**
     * 以玩家身份执行指令
     * @param player 玩家对象
     * @param command 指令（不含/）
     * @return 是否执行成功
     */
    boolean executeCommand(CommonPlayer player, String command);

    // ===== 调度器 =====

    /**
     * 获取调度器
     */
    CommonScheduler getScheduler();

    // ===== 日志 =====

    /**
     * 获取最近的日志
     * @param lines 行数
     * @return 日志列表
     */
    List<String> getRecentLogs(int lines);

    /**
     * 获取指定时间范围的日志
     * @param startTime 开始时间戳
     * @param endTime 结束时间戳
     * @return 日志列表
     */
    List<String> getLogsByTimeRange(long startTime, long endTime);

    // ===== 生命周期 =====

    /**
     * 初始化适配器
     */
    void initialize();

    /**
     * 关闭适配器
     */
    void shutdown();

    /**
     * 注册事件监听器
     */
    void registerListeners();

    /**
     * 注销事件监听器
     */
    void unregisterListeners();
}
