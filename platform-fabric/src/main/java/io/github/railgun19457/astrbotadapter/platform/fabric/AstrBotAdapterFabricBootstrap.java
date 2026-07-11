package io.github.railgun19457.astrbotadapter.platform.fabric;

import io.github.railgun19457.astrbotadapter.core.CoreRuntime;

public final class AstrBotAdapterFabricBootstrap {
    private CoreRuntime runtime;

    public void start(FabricPlatformAdapter adapter) {
        runtime = new CoreRuntime(adapter);
        runtime.start();
    }

    public void stop() {
        if (runtime != null) {
            runtime.stop();
        }
    }
}
