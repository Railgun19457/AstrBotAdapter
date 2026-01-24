package io.github.railgun19457.astrbotadapter.platform.common;

/**
 * 通用调度器接口
 * 抽象不同平台的任务调度
 */
public interface CommonScheduler {

    /**
     * 在主线程同步执行任务
     * @param task 任务
     */
    void runSync(Runnable task);

    /**
     * 在异步线程执行任务
     * @param task 任务
     */
    void runAsync(Runnable task);

    /**
     * 延迟执行任务（同步）
     * @param task 任务
     * @param delayTicks 延迟时间（tick）
     */
    void runLater(Runnable task, long delayTicks);

    /**
     * 延迟执行任务（异步）
     * @param task 任务
     * @param delayTicks 延迟时间（tick）
     */
    void runLaterAsync(Runnable task, long delayTicks);

    /**
     * 定时执行任务（同步）
     * @param task 任务
     * @param delayTicks 首次延迟（tick）
     * @param periodTicks 执行间隔（tick）
     * @return 任务ID
     */
    int runTimer(Runnable task, long delayTicks, long periodTicks);

    /**
     * 定时执行任务（异步）
     * @param task 任务
     * @param delayTicks 首次延迟（tick）
     * @param periodTicks 执行间隔（tick）
     * @return 任务ID
     */
    int runTimerAsync(Runnable task, long delayTicks, long periodTicks);

    /**
     * 取消指定任务
     * @param taskId 任务ID
     */
    void cancelTask(int taskId);

    /**
     * 取消所有任务
     */
    void cancelAll();

    /**
     * 检查当前是否在主线程
     * @return 是否在主线程
     */
    boolean isMainThread();
}
