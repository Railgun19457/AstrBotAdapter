package io.github.railgun19457.astrbotadapter.platform;

/**
 * 平台类型枚举
 */
public enum PlatformType {
    
    /** Bukkit/Spigot服务端 */
    BUKKIT("Bukkit"),
    
    /** Paper服务端 */
    PAPER("Paper"),
    
    /** Folia服务端 (Paper分支，支持区域多线程) */
    FOLIA("Folia"),
    
    /** Velocity代理端 */
    VELOCITY("Velocity"),
    
    /** 未知平台 */
    UNKNOWN("Unknown");

    private final String displayName;

    PlatformType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 是否为后端服务器（非代理端）
     */
    public boolean isBackend() {
        return this != VELOCITY;
    }

    /**
     * 是否为代理端
     */
    public boolean isProxy() {
        return this == VELOCITY;
    }

    /**
     * 是否支持Folia的区域调度
     */
    public boolean supportsRegionScheduling() {
        return this == FOLIA;
    }
}
