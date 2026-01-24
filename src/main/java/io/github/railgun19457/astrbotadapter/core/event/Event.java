package io.github.railgun19457.astrbotadapter.core.event;

/**
 * 事件基类
 * 所有内部事件都应继承此类
 */
public abstract class Event {

    private final long timestamp;

    protected Event() {
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 获取事件发生时间戳
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * 获取事件名称
     */
    public String getEventName() {
        return getClass().getSimpleName();
    }
}
