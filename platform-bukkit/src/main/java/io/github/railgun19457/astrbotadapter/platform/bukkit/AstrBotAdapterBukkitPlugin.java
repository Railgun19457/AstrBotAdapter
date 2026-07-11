package io.github.railgun19457.astrbotadapter.platform.bukkit;

import io.github.railgun19457.astrbotadapter.core.CoreRuntime;
import org.bukkit.plugin.java.JavaPlugin;

public final class AstrBotAdapterBukkitPlugin extends JavaPlugin {
    private CoreRuntime runtime;

    @Override
    public void onEnable() {
        runtime = new CoreRuntime(new BukkitPlatformAdapter(this));
        runtime.start();
    }

    @Override
    public void onDisable() {
        if (runtime != null) {
            runtime.stop();
        }
    }
}
