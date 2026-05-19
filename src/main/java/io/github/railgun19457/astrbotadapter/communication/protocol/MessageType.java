package io.github.railgun19457.astrbotadapter.communication.protocol;

/**
 * 消息类型枚举
 * 定义WebSocket通信的所有消息类型
 */
public enum MessageType {
    
    // ===== 连接管理 =====
    /** 心跳请求 */
    HEARTBEAT,
    /** 心跳响应 */
    HEARTBEAT_ACK,
    /** 连接确认 */
    CONNECTION_ACK,
    
    // ===== AI聊天 =====
    /** AI聊天请求 (MC → Astrbot) */
    CHAT_REQUEST,
    /** AI聊天响应 (Astrbot → MC) */
    CHAT_RESPONSE,
    
    // ===== 消息转发 =====
    /** 消息转发 (MC → 外部) */
    MESSAGE_FORWARD,
    /** 外部消息接收 (外部 → MC) */
    MESSAGE_INCOMING,
    
    // ===== 玩家通知 =====
    /** 玩家加入通知 */
    PLAYER_JOIN,
    /** 玩家离开通知 */
    PLAYER_QUIT,
    
    // ===== 指令相关 =====
    /** 指令执行请求 (Astrbot → MC) */
    COMMAND_REQUEST,
    /** 指令执行结果 (MC → Astrbot) */
    COMMAND_RESPONSE,
    
    // ===== 状态推送 =====
    /** 状态更新推送 */
    STATUS_UPDATE,
    
    // ===== 错误 =====
    /** 错误消息 */
    ERROR,
    /** 服务端主动断开连接通知 */
    DISCONNECT;

    /**
     * 从字符串解析消息类型
     * @param value 类型字符串
     * @return 消息类型，无法解析时返回null
     */
    public static MessageType fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
