package io.github.railgun19457.astrbotadapter.platform.bukkit;

import io.github.railgun19457.astrbotadapter.platform.spi.PlatformChatEvent;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformEventSource;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformPlayerEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.function.Supplier;

public final class BukkitEventSource implements PlatformEventSource {
    private final Plugin plugin;
    private final Supplier<BukkitChatService> chatServiceSupplier;
    private final Listener listener = new Listener() {
        @EventHandler
        public void onChat(AsyncPlayerChatEvent event) {
            if (chatListener == null) {
                return;
            }
            BukkitChatService chatService = chatServiceSupplier == null ? null : chatServiceSupplier.get();
            if (chatService != null && chatService.shouldSuppressEcho(event.getMessage())) {
                return;
            }
            chatListener.onChat(new PlatformChatEvent(event.getPlayer().getUniqueId(), event.getPlayer().getName(), event.getMessage(), event.getPlayer().getWorld().getName()));
        }

        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            if (joinListener != null) {
                joinListener.onJoin(new PlatformPlayerEvent(event.getPlayer().getUniqueId(), event.getPlayer().getName(), plugin.getServer().getOnlinePlayers().size(), plugin.getServer().getMaxPlayers(), null));
            }
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            if (leaveListener != null) {
                leaveListener.onLeave(new PlatformPlayerEvent(event.getPlayer().getUniqueId(), event.getPlayer().getName(), plugin.getServer().getOnlinePlayers().size(), plugin.getServer().getMaxPlayers(), event.getQuitMessage()));
            }
        }
    };
    private ChatListener chatListener;
    private PlayerJoinListener joinListener;
    private PlayerLeaveListener leaveListener;
    private boolean registered;

    public BukkitEventSource(Plugin plugin) {
        this(plugin, () -> null);
    }

    public BukkitEventSource(Plugin plugin, Supplier<BukkitChatService> chatServiceSupplier) {
        this.plugin = plugin;
        this.chatServiceSupplier = chatServiceSupplier == null ? () -> null : chatServiceSupplier;
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
        HandlerList.unregisterAll(listener);
        registered = false;
        chatListener = null;
        joinListener = null;
        leaveListener = null;
    }

    private void ensureRegistered() {
        if (!registered) {
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
            registered = true;
        }
    }
}
