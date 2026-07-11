package io.github.railgun19457.astrbotadapter.platform.fabric;

import io.github.railgun19457.astrbotadapter.platform.spi.PlatformChatEvent;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformEventSource;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformPlayerEvent;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

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
                chatListener.onChat(new PlatformChatEvent(sender.getUuid(), sender.getGameProfile().getName(), message.getContent().getString(), worldName(sender)));
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, ignoredServer) -> {
            if (joinListener != null) {
                ServerPlayerEntity player = handler.player;
                joinListener.onJoin(new PlatformPlayerEvent(player.getUuid(), player.getGameProfile().getName(), onlineCount(), maxPlayers(), null));
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, ignoredServer) -> {
            if (leaveListener != null) {
                ServerPlayerEntity player = handler.player;
                leaveListener.onLeave(new PlatformPlayerEvent(player.getUuid(), player.getGameProfile().getName(), Math.max(0, onlineCount() - 1), maxPlayers(), null));
            }
        });
        registered = true;
    }

    private int onlineCount() {
        return server.getPlayerManager().getPlayerList().size();
    }

    private int maxPlayers() {
        return server.getPlayerManager().getMaxPlayerCount();
    }

    private String worldName(ServerPlayerEntity player) {
        return player.getWorld().getRegistryKey().getValue().toString();
    }
}
