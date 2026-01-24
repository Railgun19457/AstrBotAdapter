package io.github.railgun19457.astrbotadapter.core.event;

/**
 * 可取消接口
 * 实现此接口的事件可以被取消
 */
public interface Cancellable {

    /**
     * 获取事件是否被取消
     */
    boolean isCancelled();

    /**
     * 设置事件取消状态
     */
    void setCancelled(boolean cancelled);
}
