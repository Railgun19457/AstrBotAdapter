package io.github.railgun19457.astrbotadapter.platform.fabric;

import io.github.railgun19457.astrbotadapter.platform.spi.PlatformChatEvent;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformEventSource;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformPlayerEvent;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

final class FabricEventSource implements PlatformEventSource {
    private final MinecraftServer server;
    private ChatListener chatListener;
    private PlayerJoinListener joinListener;
    private PlayerLeaveListener leaveListener;
    private boolean registered;

    FabricEventSource(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void registerChatListener(ChatListener listener) {
        this.chatListener = listener;
        ensureRegistered();
    }

    @Override
    public void registerJoinListener(PlayerJoinListener listener) {
        this.joinListener = listener;
        ensureRegistered();
    }

    @Override
    public void registerLeaveListener(PlayerLeaveListener listener) {
        this.leaveListener = listener;
        ensureRegistered();
    }

    @Override
    public void unregisterAll() {
        chatListener = null;
        joinListener = null;
        leaveListener = null;
    }

    private void ensureRegistered() {
        if (registered) {
            return;
        }
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            if (chatListener != null) {
                chatListener.onChat(new PlatformChatEvent(
                    sender.getUUID(),
                    sender.getGameProfile().name(),
                    message.decoratedContent().getString(),
                    worldName(sender)
                ));
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, ignoredServer) -> {
            if (joinListener != null) {
                ServerPlayer player = handler.player;
                joinListener.onJoin(new PlatformPlayerEvent(
                    player.getUUID(),
                    player.getGameProfile().name(),
                    onlineCount(),
                    maxPlayers(),
                    null
                ));
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, ignoredServer) -> {
            if (leaveListener != null) {
                ServerPlayer player = handler.player;
                leaveListener.onLeave(new PlatformPlayerEvent(
                    player.getUUID(),
                    player.getGameProfile().name(),
                    Math.max(0, onlineCount() - 1),
                    maxPlayers(),
                    null
                ));
            }
        });
        registered = true;
    }

    private int onlineCount() {
        return server.getPlayerList().getPlayers().size();
    }

    private int maxPlayers() {
        return server.getMaxPlayers();
    }

    private String worldName(ServerPlayer player) {
        return player.level().dimension().identifier().toString();
    }
}
