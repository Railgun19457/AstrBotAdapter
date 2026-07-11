package io.github.railgun19457.astrbotadapter.platform.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import io.github.railgun19457.astrbotadapter.core.CoreRuntime;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
    id = "astrbotadapter",
    name = "AstrBotAdapter",
    version = "3.0.0-SNAPSHOT",
    description = "AstrBot Minecraft server adapter for Velocity",
    authors = {"Railgun19457"}
)
public final class AstrBotAdapterVelocityPlugin {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private VelocityPlatformAdapter adapter;
    private CoreRuntime runtime;

    @Inject
    public AstrBotAdapterVelocityPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        adapter = new VelocityPlatformAdapter(this, proxy, logger, dataDirectory);
        runtime = new CoreRuntime(adapter);
        runtime.start();
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (runtime != null) {
            runtime.stop();
        }
        if (adapter != null) {
            adapter.shutdown();
        }
    }
}
