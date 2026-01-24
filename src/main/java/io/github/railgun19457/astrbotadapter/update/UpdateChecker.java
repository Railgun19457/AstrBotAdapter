package io.github.railgun19457.astrbotadapter.update;

import java.util.function.Consumer;

/**
 * 更新检查器接口
 */
public interface UpdateChecker {

    /**
     * 同步检查更新
     * @return 版本信息，如果没有更新则返回 null
     */
    VersionInfo checkForUpdate();

    /**
     * 异步检查更新
     * @param callback 回调函数，接收版本信息（如果没有更新则为 null）
     */
    void checkForUpdateAsync(Consumer<VersionInfo> callback);

    /**
     * 获取当前版本
     */
    String getCurrentVersion();

    /**
     * 是否有新版本可用
     */
    boolean isUpdateAvailable();

    /**
     * 获取缓存的最新版本信息
     */
    VersionInfo getLatestVersion();
}
