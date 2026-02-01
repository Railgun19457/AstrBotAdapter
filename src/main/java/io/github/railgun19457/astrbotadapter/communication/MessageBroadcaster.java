package io.github.railgun19457.astrbotadapter.communication;

import io.github.railgun19457.astrbotadapter.communication.protocol.Message;

/**
 * 消息广播器接口
 * 用于统一WebSocketServer和UnifiedServer的消息广播功能
 */
public interface MessageBroadcaster {

    /**
     * 广播消息到所有WebSocket客户端
     */
    void broadcast(Message message);

    /**
     * 广播原始字符串消息到所有WebSocket客户端
     */
    void broadcast(String message);

    /**
     * 检查服务是否运行中
     */
    boolean isRunning();
}
