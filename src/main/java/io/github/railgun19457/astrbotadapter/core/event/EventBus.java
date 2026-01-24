package io.github.railgun19457.astrbotadapter.core.event;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * 事件总线
 * 提供内部事件的发布/订阅机制，解耦各模块
 */
public class EventBus {

    private final Map<Class<? extends Event>, List<EventHandler<?>>> handlers;
    private final Logger logger;
    private final boolean debug;

    public EventBus(Logger logger, boolean debug) {
        this.handlers = new ConcurrentHashMap<>();
        this.logger = logger;
        this.debug = debug;
    }

    /**
     * 注册事件监听器
     * @param eventClass 事件类型
     * @param handler 处理器
     * @param <T> 事件类型
     */
    public <T extends Event> void register(Class<T> eventClass, Consumer<T> handler) {
        register(eventClass, handler, EventPriority.NORMAL);
    }

    /**
     * 注册事件监听器（带优先级）
     * @param eventClass 事件类型
     * @param handler 处理器
     * @param priority 优先级
     * @param <T> 事件类型
     */
    public <T extends Event> void register(Class<T> eventClass, Consumer<T> handler, EventPriority priority) {
        handlers.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>())
                .add(new EventHandler<>(handler, priority));
        
        // 按优先级排序
        handlers.get(eventClass).sort(Comparator.comparingInt(h -> h.priority.ordinal()));
        
        if (debug) {
            logger.info("[EventBus] 注册监听器: " + eventClass.getSimpleName());
        }
    }

    /**
     * 注销事件监听器
     * @param eventClass 事件类型
     * @param handler 处理器
     * @param <T> 事件类型
     */
    public <T extends Event> void unregister(Class<T> eventClass, Consumer<T> handler) {
        List<EventHandler<?>> list = handlers.get(eventClass);
        if (list != null) {
            list.removeIf(h -> h.handler == handler);
        }
    }

    /**
     * 注销指定事件类型的所有监听器
     * @param eventClass 事件类型
     */
    public void unregisterAll(Class<? extends Event> eventClass) {
        handlers.remove(eventClass);
    }

    /**
     * 注销所有监听器
     */
    public void unregisterAll() {
        handlers.clear();
    }

    /**
     * 发布事件
     * @param event 事件对象
     * @param <T> 事件类型
     */
    @SuppressWarnings("unchecked")
    public <T extends Event> void post(T event) {
        if (debug) {
            logger.info("[EventBus] 发布事件: " + event.getClass().getSimpleName());
        }

        List<EventHandler<?>> list = handlers.get(event.getClass());
        if (list == null || list.isEmpty()) {
            return;
        }

        for (EventHandler<?> handler : list) {
            try {
                ((EventHandler<T>) handler).handler.accept(event);
                
                // 如果事件被取消，停止传播
                if (event instanceof Cancellable && ((Cancellable) event).isCancelled()) {
                    if (debug) {
                        logger.info("[EventBus] 事件已被取消: " + event.getClass().getSimpleName());
                    }
                    break;
                }
            } catch (Exception e) {
                logger.severe("[EventBus] 事件处理异常: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * 异步发布事件
     * @param event 事件对象
     * @param <T> 事件类型
     */
    public <T extends Event> void postAsync(T event) {
        new Thread(() -> post(event), "EventBus-Async").start();
    }

    /**
     * 事件处理器包装
     */
    private static class EventHandler<T extends Event> {
        final Consumer<T> handler;
        final EventPriority priority;

        EventHandler(Consumer<T> handler, EventPriority priority) {
            this.handler = handler;
            this.priority = priority;
        }
    }

    /**
     * 事件优先级
     */
    public enum EventPriority {
        LOWEST,
        LOW,
        NORMAL,
        HIGH,
        HIGHEST,
        MONITOR  // 仅监控，不应修改事件
    }
}
