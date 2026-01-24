package io.github.railgun19457.astrbotadapter.communication.websocket;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话管理器
 * 管理所有WebSocket连接会话
 */
public class SessionManager {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /**
     * 添加会话
     */
    public void addSession(WebSocketSession session) {
        sessions.put(session.getSessionId(), session);
    }

    /**
     * 移除会话
     */
    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
    }

    /**
     * 获取会话
     */
    public WebSocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * 获取所有会话
     */
    public Collection<WebSocketSession> getAllSessions() {
        return sessions.values();
    }

    /**
     * 获取已认证的会话
     */
    public Collection<WebSocketSession> getAuthenticatedSessions() {
        return sessions.values().stream()
                .filter(WebSocketSession::isAuthenticated)
                .toList();
    }

    /**
     * 获取会话数量
     */
    public int getSessionCount() {
        return sessions.size();
    }

    /**
     * 广播消息到所有已认证会话
     * @param message 消息内容
     */
    public void broadcast(String message) {
        for (WebSocketSession session : sessions.values()) {
            if (session.isAuthenticated()) {
                session.send(message);
            }
        }
    }

    /**
     * 清理超时会话
     * @param timeoutMillis 超时时间（毫秒）
     */
    public void cleanupExpiredSessions(long timeoutMillis) {
        sessions.entrySet().removeIf(entry -> {
            WebSocketSession session = entry.getValue();
            if (!session.isAlive(timeoutMillis)) {
                session.close();
                return true;
            }
            return false;
        });
    }

    /**
     * 关闭所有会话
     */
    public void closeAll() {
        for (WebSocketSession session : sessions.values()) {
            session.close();
        }
        sessions.clear();
    }
}
