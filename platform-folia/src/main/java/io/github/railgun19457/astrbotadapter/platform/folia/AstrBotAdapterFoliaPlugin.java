package io.github.railgun19457.astrbotadapter.platform.folia;

import io.github.railgun19457.astrbotadapter.core.CoreRuntime;
import org.bukkit.plugin.java.JavaPlugin;

public final class AstrBotAdapterFoliaPlugin extends JavaPlugin {
    private FoliaPlatformAdapter adapter;
    private CoreRuntime runtime;

    @Override
    public void onEnable() {
        adapter = new FoliaPlatformAdapter(this);
        runtime = new CoreRuntime(adapter);
        runtime.start();
    }

    @Override
    public void onDisable() {
        if (runtime != null) {
            runtime.stop();
        }
        if (adapter != null) {
            adapter.shutdown();
        }
    }
}
