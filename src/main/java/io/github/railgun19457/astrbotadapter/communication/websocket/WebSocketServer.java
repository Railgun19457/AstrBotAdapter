package io.github.railgun19457.astrbotadapter.communication.websocket;

import io.github.railgun19457.astrbotadapter.communication.auth.AuthManager;
import io.github.railgun19457.astrbotadapter.communication.protocol.Message;
import io.github.railgun19457.astrbotadapter.core.config.PluginConfig;
import io.github.railgun19457.astrbotadapter.core.event.EventBus;
import io.github.railgun19457.astrbotadapter.platform.PlatformAdapter;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * WebSocket服务器
 * 基于Netty实现的WebSocket服务端
 */
public class WebSocketServer {

    private final PluginConfig config;
    private final AuthManager authManager;
    private final EventBus eventBus;
    private final PlatformAdapter platformAdapter;
    private final Logger logger;
    private final SessionManager sessionManager;
    
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private Consumer<Message> messageHandler;
    
    private volatile boolean running = false;

    public WebSocketServer(PluginConfig config, AuthManager authManager, 
                          EventBus eventBus, PlatformAdapter platformAdapter, Logger logger) {
        this.config = config;
        this.authManager = authManager;
        this.eventBus = eventBus;
        this.platformAdapter = platformAdapter;
        this.logger = logger;
        this.sessionManager = new SessionManager();
    }

    /**
     * 启动WebSocket服务器
     */
    public void start() {
        if (running) {
            logger.warning("WebSocket服务器已在运行中");
            return;
        }

        String host = config.getWsHost();
        int port = config.getWsPort();

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            
                            // HTTP编解码
                            pipeline.addLast(new HttpServerCodec());
                            pipeline.addLast(new ChunkedWriteHandler());
                            pipeline.addLast(new HttpObjectAggregator(65536));
                            
                            // 空闲检测
                            pipeline.addLast(new IdleStateHandler(
                                    config.getHeartbeatTimeout(), 0, 0, TimeUnit.SECONDS));
                            
                            // 自定义处理器
                            pipeline.addLast(new WebSocketHandler(
                                    authManager, eventBus, platformAdapter, 
                                    logger, sessionManager, messageHandler));
                        }
                    });

            ChannelFuture future;
            if ("0.0.0.0".equals(host)) {
                future = bootstrap.bind(port).sync();
            } else {
                future = bootstrap.bind(host, port).sync();
            }
            
            serverChannel = future.channel();
            running = true;
            
            logger.info("WebSocket服务器已启动 - " + host + ":" + port);
            
            // 启动心跳检测任务
            startHeartbeatChecker();

        } catch (Exception e) {
            logger.severe("WebSocket服务器启动失败: " + e.getMessage());
            stop();
        }
    }

    /**
     * 停止WebSocket服务器
     */
    public void stop() {
        running = false;
        
        // 关闭所有会话
        sessionManager.closeAll();
        
        // 关闭服务器通道
        if (serverChannel != null) {
            serverChannel.close();
            serverChannel = null;
        }

        // 关闭线程池
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
            bossGroup = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
            workerGroup = null;
        }

        logger.info("WebSocket服务器已停止");
    }

    /**
     * 广播消息到所有客户端
     */
    public void broadcast(Message message) {
        sessionManager.broadcast(message.toJson());
    }

    /**
     * 广播消息到所有客户端
     */
    public void broadcast(String message) {
        sessionManager.broadcast(message);
    }

    /**
     * 发送消息到指定会话
     */
    public void send(String sessionId, Message message) {
        WebSocketSession session = sessionManager.getSession(sessionId);
        if (session != null) {
            session.send(message.toJson());
        }
    }

    /**
     * 设置消息处理器
     */
    public void setMessageHandler(Consumer<Message> messageHandler) {
        this.messageHandler = messageHandler;
    }

    /**
     * 获取会话管理器
     */
    public SessionManager getSessionManager() {
        return sessionManager;
    }

    /**
     * 获取连接数
     */
    public int getConnectionCount() {
        return sessionManager.getSessionCount();
    }

    /**
     * 是否正在运行
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * 启动心跳检测定时任务
     */
    private void startHeartbeatChecker() {
        if (workerGroup != null) {
            workerGroup.scheduleAtFixedRate(() -> {
                if (running) {
                    long timeout = config.getHeartbeatTimeout() * 1000L;
                    sessionManager.cleanupExpiredSessions(timeout);
                }
            }, config.getHeartbeatInterval(), config.getHeartbeatInterval(), TimeUnit.SECONDS);
        }
    }
}
