package io.github.railgun19457.astrbotadapter.platform.common;

import java.util.UUID;

/**
 * 通用玩家接口
 * 抽象不同平台的玩家对象
 */
public interface CommonPlayer {

    /**
     * 获取玩家UUID
     */
    UUID getUniqueId();

    /**
     * 获取玩家名称
     */
    String getName();

    /**
     * 获取玩家显示名称（可能包含颜色代码）
     */
    String getDisplayName();

    /**
     * 获取玩家延迟（毫秒）
     */
    int getPing();

    /**
     * 发送消息给玩家
     */
    void sendMessage(String message);

    /**
     * 检查玩家是否拥有指定权限
     */
    boolean hasPermission(String permission);

    /**
     * 玩家是否在线
     */
    boolean isOnline();

    // ===== 以下方法仅后端服务器可用 =====

    /**
     * 获取玩家血量
     * @return 血量，代理端返回-1
     */
    default double getHealth() {
        return -1;
    }

    /**
     * 获取玩家最大血量
     * @return 最大血量，代理端返回-1
     */
    default double getMaxHealth() {
        return -1;
    }

    /**
     * 获取玩家等级
     * @return 等级，代理端返回-1
     */
    default int getLevel() {
        return -1;
    }

    /**
     * 获取玩家当前所在世界名称
     * @return 世界名称，代理端返回null
     */
    default String getWorld() {
        return null;
    }

    /**
     * 获取玩家位置信息
     * @return 位置信息，代理端返回null
     */
    default PlayerLocation getLocation() {
        return null;
    }

    /**
     * 获取玩家游戏模式
     * @return 游戏模式，代理端返回null
     */
    default String getGameMode() {
        return null;
    }

    /**
     * 获取玩家饥饿值
     * @return 饥饿值，代理端返回-1
     */
    default int getFoodLevel() {
        return -1;
    }

    /**
     * 获取玩家经验比例
     * @return 经验比例(0-1)，代理端返回-1
     */
    default float getExp() {
        return -1;
    }

    /**
     * 获取玩家总经验
     * @return 总经验，代理端返回-1
     */
    default int getTotalExp() {
        return -1;
    }

    /**
     * 是否为管理员
     * @return 是否为OP，代理端返回false
     */
    default boolean isOp() {
        return false;
    }

    /**
     * 是否正在飞行
     * @return 是否飞行，代理端返回false
     */
    default boolean isFlying() {
        return false;
    }

    /**
     * 获取首次进入服务器时间戳
     * @return 时间戳(毫秒)，代理端返回-1
     */
    default long getFirstPlayed() {
        return -1;
    }

    /**
     * 获取最后一次离线时间戳
     * @return 时间戳(毫秒)，代理端返回-1
     */
    default long getLastPlayed() {
        return -1;
    }

    /**
     * 获取在线时长
     * @return 在线时长(毫秒)，代理端返回-1
     */
    default long getOnlineTime() {
        return -1;
    }

    /**
     * 获取玩家当前连接的服务器名称（仅代理端有效）
     * @return 服务器名称，后端返回null
     */
    default String getConnectedServer() {
        return null;
    }

    /**
     * 玩家位置
     */
    class PlayerLocation {
        private final String world;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;

        public PlayerLocation(String world, double x, double y, double z, float yaw, float pitch) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public String getWorld() {
            return world;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;
        }

        public float getYaw() {
            return yaw;
        }

        public float getPitch() {
            return pitch;
        }

        @Override
        public String toString() {
            return String.format("%s (%.2f, %.2f, %.2f)", world, x, y, z);
        }
    }
}
