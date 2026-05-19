package io.github.railgun19457.astrbotadapter.communication;

import com.google.gson.JsonObject;
import io.github.railgun19457.astrbotadapter.communication.auth.AuthManager;
import io.github.railgun19457.astrbotadapter.communication.auth.AuthResult;
import io.github.railgun19457.astrbotadapter.communication.protocol.ErrorCode;
import io.github.railgun19457.astrbotadapter.communication.protocol.Message;
import io.github.railgun19457.astrbotadapter.communication.protocol.MessageType;
import io.github.railgun19457.astrbotadapter.communication.protocol.ProtocolInfo;
import io.github.railgun19457.astrbotadapter.communication.rest.HttpRequestDispatcher;
import io.github.railgun19457.astrbotadapter.communication.websocket.SessionManager;
import io.github.railgun19457.astrbotadapter.communication.websocket.WebSocketSession;
import io.github.railgun19457.astrbotadapter.core.config.PluginConfig;
import io.github.railgun19457.astrbotadapter.core.event.EventBus;
import io.github.railgun19457.astrbotadapter.platform.PlatformAdapter;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.websocketx.*;

import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * 统一请求处理器
 * 根据请求路径分流到WebSocket或REST API处理
 */
public class UnifiedRequestHandler extends SimpleChannelInboundHandler<Object> {

    private static final String WS_PATH = "/ws";
    private static final String API_PATH_PREFIX = "/api";

    private final AuthManager authManager;
    private final EventBus eventBus;
    private final PlatformAdapter platformAdapter;
    private final Logger logger;
    private final SessionManager sessionManager;
    private final BiConsumer<WebSocketSession, Message> messageHandler;
    private final HttpRequestDispatcher restDispatcher;
    private final PluginConfig config;
    
    private WebSocketServerHandshaker handshaker;
    private WebSocketSession session;
    private boolean isWebSocket = false;

    public UnifiedRequestHandler(AuthManager authManager, EventBus eventBus,
                                  PlatformAdapter platformAdapter, Logger logger,
                                  SessionManager sessionManager, BiConsumer<WebSocketSession, Message> messageHandler,
                                  HttpRequestDispatcher restDispatcher, PluginConfig config) {
        this.authManager = authManager;
        this.eventBus = eventBus;
        this.platformAdapter = platformAdapter;
        this.logger = logger;
        this.sessionManager = sessionManager;
        this.messageHandler = messageHandler;
        this.restDispatcher = restDispatcher;
        this.config = config;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            handleHttpRequest(ctx, (FullHttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
    }

    /**
     * 处理HTTP请求，根据路径分流
     */
    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
        String uri = req.uri();
        String path = getPath(uri);

        // WebSocket握手请求
        if (path.equals(WS_PATH) || path.startsWith(WS_PATH + "?")) {
            handleWebSocketHandshake(ctx, req);
            return;
        }

        // REST API请求
        if (path.startsWith(API_PATH_PREFIX)) {
            // 转发到REST处理器
            try {
                restDispatcher.channelRead(ctx, req.retain());
            } catch (Exception e) {
                logger.warning("REST请求处理异常: " + e.getMessage());
            }
            return;
        }

        // 未知路径，返回404
        sendHttpNotFound(ctx, req);
    }

    /**
     * 处理WebSocket握手
     */
    private void handleWebSocketHandshake(ChannelHandlerContext ctx, FullHttpRequest req) {
        // 验证Token
        String uri = req.uri();
        AuthResult authResult = authManager.authenticateUrl(uri);
        
        if (!authResult.isSuccess()) {
            logger.warning("WebSocket连接认证失败: " + authResult.getMessage() + " - " + ctx.channel().remoteAddress());
            sendErrorAndClose(ctx, ErrorCode.AUTH_TOKEN_INVALID);
            return;
        }

        // WebSocket握手
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                getWebSocketLocation(req), null, true);
        handshaker = wsFactory.newHandshaker(req);
        
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            handshaker.handshake(ctx.channel(), req);
            
            // 创建会话
            session = new WebSocketSession(ctx.channel());
            session.setAuthenticated(true);
            sessionManager.addSession(session);
            isWebSocket = true;
            
            logger.info("WebSocket客户端已连接: " + session.getRemoteAddress());
            
            // 发送连接确认
            sendConnectionAck(ctx.channel());
        }
    }

    /**
     * 处理WebSocket帧
     */
    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        // 关闭帧
        if (frame instanceof CloseWebSocketFrame) {
            if (handshaker != null) {
                handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            }
            return;
        }
        
        // Ping帧
        if (frame instanceof PingWebSocketFrame) {
            ctx.channel().writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
            return;
        }
        
        // Pong帧
        if (frame instanceof PongWebSocketFrame) {
            if (session != null) {
                session.updateHeartbeat();
            }
            return;
        }
        
        // 文本帧
        if (frame instanceof TextWebSocketFrame) {
            String text = ((TextWebSocketFrame) frame).text();
            handleTextMessage(ctx, text);
            return;
        }
        
        // 不支持的帧类型
        logger.warning("不支持的WebSocket帧类型: " + frame.getClass().getName());
    }

    /**
     * 处理WebSocket文本消息
     */
    private void handleTextMessage(ChannelHandlerContext ctx, String text) {
        try {
            Message message = Message.fromJson(text);
            
            if (message == null || message.getType() == null) {
                sendError(ctx.channel(), ErrorCode.REQUEST_FORMAT_ERROR, "消息格式错误");
                return;
            }

            // 更新心跳
            if (session != null) {
                session.updateHeartbeat();
            }

            // 处理心跳
            if (message.getType() == MessageType.HEARTBEAT) {
                handleHeartbeat(ctx.channel(), message);
                return;
            }

            // 交给外部处理器
            if (messageHandler != null) {
                messageHandler.accept(session, message);
            }

        } catch (Exception e) {
            logger.warning("处理WebSocket消息异常: " + e.getMessage());
            sendError(ctx.channel(), ErrorCode.REQUEST_FORMAT_ERROR, e.getMessage());
        }
    }

    /**
     * 处理心跳
     */
    private void handleHeartbeat(Channel channel, Message message) {
        Message response = Message.builder()
                .type(MessageType.HEARTBEAT_ACK)
                .id(message.getId())
                .build();
        channel.writeAndFlush(new TextWebSocketFrame(response.toJson()));
    }

    /**
     * 发送连接确认
     */
    private void sendConnectionAck(Channel channel) {
        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", platformAdapter.getServerName());
        serverInfo.addProperty("platform", platformAdapter.getPlatformType().getDisplayName());
        serverInfo.addProperty("version", platformAdapter.getServerVersion());

        JsonObject payload = new JsonObject();
        ProtocolInfo.addTo(payload);
        payload.addProperty("sessionId", session.getSessionId());
        payload.add("serverInfo", serverInfo);

        Message ack = Message.builder()
                .type(MessageType.CONNECTION_ACK)
                .payload(payload)
                .build();

        channel.writeAndFlush(new TextWebSocketFrame(ack.toJson()));
    }

    /**
     * 发送错误消息
     */
    private void sendError(Channel channel, ErrorCode errorCode, String detail) {
        JsonObject payload = new JsonObject();
        payload.addProperty("code", errorCode.getCode());
        payload.addProperty("message", errorCode.getMessage());
        if (detail != null) {
            payload.addProperty("detail", detail);
        }

        Message error = Message.builder()
                .type(MessageType.ERROR)
                .payload(payload)
                .build();

        channel.writeAndFlush(new TextWebSocketFrame(error.toJson()));
    }

    /**
     * 发送错误并关闭连接
     */
    private void sendErrorAndClose(ChannelHandlerContext ctx, ErrorCode errorCode) {
        JsonObject payload = new JsonObject();
        payload.addProperty("code", errorCode.getCode());
        payload.addProperty("message", errorCode.getMessage());

        Message error = Message.builder()
                .type(MessageType.ERROR)
                .payload(payload)
                .build();

        ctx.channel().writeAndFlush(new TextWebSocketFrame(error.toJson()))
                .addListener(future -> ctx.close());
    }

    /**
     * 发送HTTP 404响应
     */
    private void sendHttpNotFound(ChannelHandlerContext ctx, FullHttpRequest req) {
        io.netty.handler.codec.http.FullHttpResponse response = new io.netty.handler.codec.http.DefaultFullHttpResponse(
                io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND);
        response.headers().set(io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH, 0);
        ctx.writeAndFlush(response).addListener(io.netty.channel.ChannelFutureListener.CLOSE);
    }

    private String getPath(String uri) {
        int queryIndex = uri.indexOf('?');
        return queryIndex > 0 ? uri.substring(0, queryIndex) : uri;
    }

    private String getWebSocketLocation(FullHttpRequest req) {
        return "ws://" + req.headers().get("Host") + req.uri();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (isWebSocket && session != null) {
            sessionManager.removeSession(session.getSessionId());
            logger.info("WebSocket客户端已断开: " + session.getRemoteAddress());
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.warning("连接异常: " + cause.getMessage());
        ctx.close();
    }
}
