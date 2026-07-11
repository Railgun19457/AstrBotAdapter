package io.github.railgun19457.astrbotadapter.platform.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformAdapter;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformChatService;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformCommandRegistry;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformCommandService;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformConfigStore;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformEventSource;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformInfo;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformKeyStore;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformLogService;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformLogger;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformPermissionService;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformPlayerService;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformScheduler;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformServerService;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformTextService;
import io.github.railgun19457.astrbotadapter.platform.spi.RuntimeMode;
import io.github.railgun19457.astrbotadapter.platform.spi.support.ExecutorPlatformScheduler;
import io.github.railgun19457.astrbotadapter.platform.spi.support.NoopPlatformComponents;
import io.github.railgun19457.astrbotadapter.protocol.ServerRole;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

final class VelocityPlatformAdapter implements PlatformAdapter {
    private final ProxyServer proxy;
    private final ScheduledExecutorService executor;
    private final PlatformLogger logger;
    private final PlatformScheduler scheduler;
    private final PlatformConfigStore configStore;
    private final PlatformKeyStore keyStore;
    private final PlatformCommandRegistry commandRegistry;
    private final PlatformPlayerService playerService;
    private final PlatformChatService chatService;
    private final PlatformCommandService commandService;
    private final PlatformServerService serverService;
    private final PlatformPermissionService permissionService;

    VelocityPlatformAdapter(Object plugin, ProxyServer proxy, Logger logger, Path dataDirectory) {
        this.proxy = proxy;
        this.executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "AstrBotAdapter-Velocity-Core");
            thread.setDaemon(true);
            return thread;
        });
        this.logger = new VelocityPlatformLogger(logger);
        this.scheduler = new ExecutorPlatformScheduler(executor);
        this.configStore = NoopPlatformComponents.configStore(dataDirectory);
        this.keyStore = NoopPlatformComponents.keyStore(dataDirectory.resolve("keys"));
        this.commandRegistry = new VelocityCommandRegistry(plugin, proxy);
        this.playerService = new VelocityPlayerService(proxy, scheduler);
        this.chatService = new VelocityChatService(proxy, scheduler);
        this.commandService = new VelocityCommandService(proxy);
        this.serverService = new VelocityServerService(proxy, scheduler);
        this.permissionService = new VelocityPermissionService(proxy);
    }

    void shutdown() {
        executor.shutdownNow();
    }

    @Override
    public PlatformInfo platformInfo() {
        return new PlatformInfo(
            "velocity",
            proxy.getVersion().getVersion(),
            "proxy",
            "velocity-main",
            "Velocity",
            ServerRole.VELOCITY
        );
    }

    @Override
    public RuntimeMode runtimeMode() {
        return RuntimeMode.VELOCITY;
    }

    @Override
    public PlatformLogger logger() {
        return logger;
    }

    @Override
    public PlatformScheduler scheduler() {
        return scheduler;
    }

    @Override
    public PlatformConfigStore configStore() {
        return configStore;
    }

    @Override
    public PlatformKeyStore keyStore() {
        return keyStore;
    }

    @Override
    public PlatformCommandRegistry commandRegistry() {
        return commandRegistry;
    }

    @Override
    public PlatformEventSource eventSource() {
        return NoopPlatformComponents.EVENT_SOURCE;
    }

    @Override
    public Optional<PlatformPlayerService> playerService() {
        return Optional.of(playerService);
    }

    @Override
    public Optional<PlatformChatService> chatService() {
        return Optional.of(chatService);
    }

    @Override
    public Optional<PlatformCommandService> commandService() {
        return Optional.of(commandService);
    }

    @Override
    public Optional<PlatformServerService> serverService() {
        return Optional.of(serverService);
    }

    @Override
    public Optional<PlatformLogService> logService() {
        return Optional.empty();
    }

    @Override
    public Optional<PlatformPermissionService> permissionService() {
        return Optional.of(permissionService);
    }

    @Override
    public PlatformTextService textService() {
        return NoopPlatformComponents.TEXT_SERVICE;
    }
}
