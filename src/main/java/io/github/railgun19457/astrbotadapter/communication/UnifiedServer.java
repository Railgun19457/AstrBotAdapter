package io.github.railgun19457.astrbotadapter.communication;

import io.github.railgun19457.astrbotadapter.communication.auth.AuthManager;
import io.github.railgun19457.astrbotadapter.communication.protocol.Message;
import io.github.railgun19457.astrbotadapter.communication.rest.HttpRequestDispatcher;
import io.github.railgun19457.astrbotadapter.communication.rest.controller.CommandController;
import io.github.railgun19457.astrbotadapter.communication.rest.controller.LogController;
import io.github.railgun19457.astrbotadapter.communication.rest.controller.PlayerController;
import io.github.railgun19457.astrbotadapter.communication.rest.controller.ServerController;
import io.github.railgun19457.astrbotadapter.communication.websocket.SessionManager;
import io.github.railgun19457.astrbotadapter.communication.websocket.WebSocketSession;
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
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * 统一通信服务器
 * 在同一个端口上同时提供WebSocket和REST API服务
 * 使用路径分流：/ws -> WebSocket, /api/* -> REST API
 */
public class UnifiedServer implements MessageBroadcaster {

    private final PluginConfig config;
    private final AuthManager authManager;
    private final EventBus eventBus;
    private final PlatformAdapter platformAdapter;
    private final Logger logger;
    
    // WebSocket相关
    private final SessionManager sessionManager;
    private Consumer<Message> messageHandler;
    
    // REST相关
    private HttpRequestDispatcher dispatcher;
    
    // Netty组件
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    
    private volatile boolean running = false;

    public UnifiedServer(PluginConfig config, AuthManager authManager,
                        EventBus eventBus, PlatformAdapter platformAdapter, Logger logger) {
        this.config = config;
        this.authManager = authManager;
        this.eventBus = eventBus;
        this.platformAdapter = platformAdapter;
        this.logger = logger;
        this.sessionManager = new SessionManager();
    }

    /**
     * 启动统一服务器
     */
    public void start() {
        if (running) {
            logger.warning("统一服务器已在运行中");
            return;
        }

        String host = config.getServerHost();
        int port = config.getServerPort();

        // 创建REST请求分发器
        dispatcher = new HttpRequestDispatcher(authManager, logger, config.getRateLimit());
        registerDefaultRoutes();

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
                            
                            // 统一路由处理器
                            pipeline.addLast(new UnifiedRequestHandler(
                                    authManager, eventBus, platformAdapter, logger,
                                    sessionManager, messageHandler, dispatcher, config));
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
            
            logger.info("统一服务器已启动 - " + host + ":" + port);
            logger.info("  WebSocket路径: /ws");
            logger.info("  REST API路径: /api/*");
            
            // 启动心跳检测任务
            startHeartbeatChecker();

        } catch (Exception e) {
            logger.severe("统一服务器启动失败: " + e.getMessage());
            stop();
        }
    }

    /**
     * 停止服务器
     */
    public void stop() {
        running = false;
        
        // 关闭所有WebSocket会话
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

        logger.info("统一服务器已停止");
    }

    /**
     * 广播WebSocket消息到所有客户端
     */
    public void broadcast(Message message) {
        sessionManager.broadcast(message.toJson());
    }

    /**
     * 广播WebSocket消息到所有客户端
     */
    public void broadcast(String message) {
        sessionManager.broadcast(message);
    }

    /**
     * 发送WebSocket消息到指定会话
     */
    public void send(String sessionId, Message message) {
        WebSocketSession session = sessionManager.getSession(sessionId);
        if (session != null) {
            session.send(message.toJson());
        }
    }

    /**
     * 设置WebSocket消息处理器
     */
    public void setMessageHandler(Consumer<Message> messageHandler) {
        this.messageHandler = messageHandler;
    }

    /**
     * 获取WebSocket会话管理器
     */
    public SessionManager getSessionManager() {
        return sessionManager;
    }

    /**
     * 获取WebSocket连接数
     */
    public int getConnectionCount() {
        return sessionManager.getSessionCount();
    }

    /**
     * 注册REST API路由
     */
    public void registerRoute(String path, HttpRequestDispatcher.RouteHandler handler) {
        if (dispatcher != null) {
            dispatcher.registerRoute(path, handler);
        }
    }

    /**
     * 获取REST请求分发器
     */
    public HttpRequestDispatcher getDispatcher() {
        return dispatcher;
    }

    /**
     * 是否正在运行
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * 注册默认API路由
     */
    private void registerDefaultRoutes() {
        ServerController serverController = new ServerController(platformAdapter);
        PlayerController playerController = new PlayerController(platformAdapter);
        CommandController commandController = new CommandController(platformAdapter, config, logger);
        LogController logController = new LogController(platformAdapter, config);

        serverController.registerRoutes(dispatcher);
        playerController.registerRoutes(dispatcher);
        commandController.registerRoutes(dispatcher);
        logController.registerRoutes(dispatcher);
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
