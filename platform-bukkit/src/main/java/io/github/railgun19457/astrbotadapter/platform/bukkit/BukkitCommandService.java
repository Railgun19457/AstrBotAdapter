package io.github.railgun19457.astrbotadapter.platform.bukkit;

import io.github.railgun19457.astrbotadapter.platform.spi.PlatformCommandService;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformScheduler;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class BukkitCommandService implements PlatformCommandService {
    private final Plugin plugin;
    private final PlatformScheduler scheduler;

    public BukkitCommandService(Plugin plugin, PlatformScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    @Override
    public CompletionStage<CommandResult> execute(CommandExecutionRequest request) {
        CompletableFuture<CommandResult> future = new CompletableFuture<>();
        long started = System.currentTimeMillis();
        scheduler.global(() -> {
            CommandSender sender = plugin.getServer().getConsoleSender();
            if ("player".equalsIgnoreCase(request.executorType())) {
                Player player = plugin.getServer().getPlayerExact(request.playerName());
                if (player == null) {
                    future.complete(new CommandResult(false, false, "player not online", 1, System.currentTimeMillis() - started));
                    return;
                }
                sender = player;
            }
            boolean result = plugin.getServer().dispatchCommand(sender, request.command());
            future.complete(new CommandResult(true, result, result ? "command dispatched" : "command failed", result ? 0 : 1, System.currentTimeMillis() - started));
        });
        return future;
    }
}
