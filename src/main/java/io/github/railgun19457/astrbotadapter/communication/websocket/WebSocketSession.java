package io.github.railgun19457.astrbotadapter.communication.websocket;

import java.util.UUID;

/**
 * WebSocket会话
 * 封装单个客户端连接的信息
 */
public class WebSocketSession {

    private final String sessionId;
    private final io.netty.channel.Channel channel;
    private final long connectedTime;
    private final String remoteAddress;
    
    private long lastHeartbeat;
    private boolean authenticated;
    private String clientInfo;

    public WebSocketSession(io.netty.channel.Channel channel) {
        this.sessionId = UUID.randomUUID().toString();
        this.channel = channel;
        this.connectedTime = System.currentTimeMillis();
        this.lastHeartbeat = connectedTime;
        this.authenticated = false;
        
        if (channel.remoteAddress() != null) {
            this.remoteAddress = channel.remoteAddress().toString();
        } else {
            this.remoteAddress = "unknown";
        }
    }

    public String getSessionId() {
        return sessionId;
    }

    public io.netty.channel.Channel getChannel() {
        return channel;
    }

    public long getConnectedTime() {
        return connectedTime;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void updateHeartbeat() {
        this.lastHeartbeat = System.currentTimeMillis();
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public String getClientInfo() {
        return clientInfo;
    }

    public void setClientInfo(String clientInfo) {
        this.clientInfo = clientInfo;
    }

    /**
     * 检查会话是否活跃
     * @param timeoutMillis 超时时间（毫秒）
     * @return 是否活跃
     */
    public boolean isAlive(long timeoutMillis) {
        return channel.isActive() && 
               (System.currentTimeMillis() - lastHeartbeat) < timeoutMillis;
    }

    /**
     * 获取连接时长（毫秒）
     */
    public long getConnectionDuration() {
        return System.currentTimeMillis() - connectedTime;
    }

    /**
     * 发送消息
     * @param message 消息内容
     */
    public void send(String message) {
        if (channel.isActive()) {
            channel.writeAndFlush(new io.netty.handler.codec.http.websocketx.TextWebSocketFrame(message));
        }
    }

    /**
     * 关闭会话
     */
    public void close() {
        if (channel.isActive()) {
            channel.close();
        }
    }

    @Override
    public String toString() {
        return "WebSocketSession{" +
                "sessionId='" + sessionId + '\'' +
                ", remoteAddress='" + remoteAddress + '\'' +
                ", authenticated=" + authenticated +
                '}';
    }
}
