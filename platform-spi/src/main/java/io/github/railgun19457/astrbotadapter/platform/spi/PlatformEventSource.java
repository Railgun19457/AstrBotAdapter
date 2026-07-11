package io.github.railgun19457.astrbotadapter.platform.spi;

public interface PlatformEventSource {
    void registerChatListener(ChatListener listener);

    void registerJoinListener(PlayerJoinListener listener);

    void registerLeaveListener(PlayerLeaveListener listener);

    void unregisterAll();

    @FunctionalInterface
    interface ChatListener {
        void onChat(PlatformChatEvent event);
    }

    @FunctionalInterface
    interface PlayerJoinListener {
        void onJoin(PlatformPlayerEvent event);
    }

    @FunctionalInterface
    interface PlayerLeaveListener {
        void onLeave(PlatformPlayerEvent event);
    }
}
