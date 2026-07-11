package io.github.railgun19457.astrbotadapter.platform.fabric;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class AstrBotAdapterFabricMod implements DedicatedServerModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("AstrBotAdapter");

    private AstrBotAdapterFabricBootstrap bootstrap;
    private FabricPlatformAdapter adapter;

    @Override
    public void onInitializeServer() {
        FabricCommandRegistry.registerDispatcher();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Path dataDirectory = FabricLoader.getInstance().getConfigDir().resolve("AstrBotAdapter");
            adapter = new FabricPlatformAdapter(server, dataDirectory, LOGGER);
            bootstrap = new AstrBotAdapterFabricBootstrap();
            bootstrap.start(adapter);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (bootstrap != null) {
                bootstrap.stop();
                bootstrap = null;
            }
            if (adapter != null) {
                adapter.shutdown();
                adapter = null;
            }
        });
    }
}
