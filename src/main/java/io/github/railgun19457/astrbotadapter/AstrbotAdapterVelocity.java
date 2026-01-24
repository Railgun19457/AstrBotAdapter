package io.github.railgun19457.astrbotadapter;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import io.github.railgun19457.astrbotadapter.platform.velocity.VelocityAdapter;
import io.github.railgun19457.astrbotadapter.platform.velocity.listener.VelocityChatListener;
import io.github.railgun19457.astrbotadapter.platform.velocity.listener.VelocityPlayerListener;
import org.slf4j.Logger;

import java.io.File;
import java.nio.file.Path;

/**
 * AstrbotAdapter Velocity插件入口
 */
@Plugin(
        id = "astrbotadapter",
        name = "AstrbotAdapter",
        version = "2.0.0",
        description = "Astrbot框架的Minecraft服务器适配器",
        authors = {"Railgun19457"}
)
public class AstrbotAdapterVelocity extends AstrbotAdapterPlugin {

    private final ProxyServer proxy;
    private final Logger velocityLogger;
    private final Path dataDirectory;

    @Inject
    public AstrbotAdapterVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.velocityLogger = logger;
        this.dataDirectory = dataDirectory;
        
        // 创建Java Logger包装
        this.logger = java.util.logging.Logger.getLogger("AstrbotAdapter");
        this.logger.setUseParentHandlers(false);
        this.logger.addHandler(new VelocityLogHandler(velocityLogger));
        
        this.dataFolder = dataDirectory.toFile();
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        initialize();
        registerVelocityListeners();
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        shutdown();
    }

    @Override
    protected void initializePlatform() {
        platformAdapter = new VelocityAdapter(this, proxy, velocityLogger);
        platformAdapter.initialize();
        logger.info("Velocity平台适配器已初始化");
    }

    /**
     * 注册Velocity事件监听器
     */
    private void registerVelocityListeners() {
        // 注册聊天监听器
        proxy.getEventManager().register(this, 
                new VelocityChatListener(chatService, messageForwardService));
        
        // 注册玩家监听器
        proxy.getEventManager().register(this, 
                new VelocityPlayerListener(this, proxy, notificationService));
        
        logger.info("Velocity事件监听器已注册");
    }

    /**
     * Velocity Logger适配器
     */
    private static class VelocityLogHandler extends java.util.logging.Handler {
        private final Logger velocityLogger;

        public VelocityLogHandler(Logger velocityLogger) {
            this.velocityLogger = velocityLogger;
        }

        @Override
        public void publish(java.util.logging.LogRecord record) {
            String message = record.getMessage();
            java.util.logging.Level level = record.getLevel();
            
            if (level == java.util.logging.Level.SEVERE) {
                velocityLogger.error(message);
            } else if (level == java.util.logging.Level.WARNING) {
                velocityLogger.warn(message);
            } else if (level == java.util.logging.Level.INFO) {
                velocityLogger.info(message);
            } else {
                velocityLogger.debug(message);
            }
        }

        @Override
        public void flush() {}

        @Override
        public void close() throws SecurityException {}
    }
}
