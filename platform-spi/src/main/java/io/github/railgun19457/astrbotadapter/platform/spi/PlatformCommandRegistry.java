package io.github.railgun19457.astrbotadapter.platform.spi;

public interface PlatformCommandRegistry {
    void registerCoreCommands(PlatformRuntimeControl runtimeControl);

    void unregisterAll();
}
