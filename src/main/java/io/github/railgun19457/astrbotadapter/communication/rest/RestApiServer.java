package io.github.railgun19457.astrbotadapter.communication.rest;

import io.github.railgun19457.astrbotadapter.communication.auth.AuthManager;
import io.github.railgun19457.astrbotadapter.core.config.PluginConfig;
import io.github.railgun19457.astrbotadapter.communication.rest.controller.CommandController;
import io.github.railgun19457.astrbotadapter.communication.rest.controller.LogController;
import io.github.railgun19457.astrbotadapter.communication.rest.controller.PlayerController;
import io.github.railgun19457.astrbotadapter.communication.rest.controller.ServerController;
import io.github.railgun19457.astrbotadapter.platform.PlatformAdapter;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

import java.util.logging.Logger;

/**
 * REST API服务器
 * 基于Netty实现的HTTP服务端
 */
public class RestApiServer {

    private final PluginConfig config;
    private final AuthManager authManager;
    private final PlatformAdapter platformAdapter;
    private final Logger logger;
    
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private HttpRequestDispatcher dispatcher;
    
    private volatile boolean running = false;

    public RestApiServer(PluginConfig config, AuthManager authManager, 
                        PlatformAdapter platformAdapter, Logger logger) {
        this.config = config;
        this.authManager = authManager;
        this.platformAdapter = platformAdapter;
        this.logger = logger;
    }

    /**
     * 启动REST API服务器
     */
    public void start() {
        if (running) {
            logger.warning("REST API服务器已在运行中");
            return;
        }

        String host = config.getRestHost();
        int port = config.getRestPort();

        // 创建请求分发器
        dispatcher = new HttpRequestDispatcher(authManager, logger, config.getRateLimit());
        
        // 注册默认路由
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
                            pipeline.addLast(new HttpServerCodec());
                            pipeline.addLast(new HttpObjectAggregator(65536));
                            pipeline.addLast(dispatcher);
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
            
            logger.info("REST API服务器已启动 - " + host + ":" + port);

        } catch (Exception e) {
            logger.severe("REST API服务器启动失败: " + e.getMessage());
            stop();
        }
    }

    /**
     * 停止REST API服务器
     */
    public void stop() {
        running = false;
        
        if (serverChannel != null) {
            serverChannel.close();
            serverChannel = null;
        }

        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
            bossGroup = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
            workerGroup = null;
        }

        logger.info("REST API服务器已停止");
    }

    /**
     * 注册路由
     */
    public void registerRoute(String path, HttpRequestDispatcher.RouteHandler handler) {
        if (dispatcher != null) {
            dispatcher.registerRoute(path, handler);
        }
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
     * 获取请求分发器
     */
    public HttpRequestDispatcher getDispatcher() {
        return dispatcher;
    }
}
