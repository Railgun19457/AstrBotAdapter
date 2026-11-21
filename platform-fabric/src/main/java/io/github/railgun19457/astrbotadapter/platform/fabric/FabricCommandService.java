package io.github.railgun19457.astrbotadapter.platform.fabric;

import io.github.railgun19457.astrbotadapter.platform.spi.PlatformCommandService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

final class FabricCommandService implements PlatformCommandService {
    private final MinecraftServer server;
    private final FabricPlatformScheduler scheduler;

    FabricCommandService(MinecraftServer server, FabricPlatformScheduler scheduler) {
        this.server = server;
        this.scheduler = scheduler;
    }

    @Override
    public CompletionStage<CommandResult> execute(CommandExecutionRequest request) {
        CompletableFuture<CommandResult> future = new CompletableFuture<>();
        long started = System.currentTimeMillis();
        scheduler.global(() -> {
            CommandSourceStack source = server.createCommandSourceStack();
            if ("player".equalsIgnoreCase(request.executorType())) {
                ServerPlayer player = findPlayer(request.playerName());
                if (player == null) {
                    future.complete(new CommandResult(false, false, "player not online", 1, System.currentTimeMillis() - started));
                    return;
                }
                source = player.createCommandSourceStack();
            }
            try {
                server.getCommands().performPrefixedCommand(source, request.command());
                future.complete(new CommandResult(true, true, "command dispatched", 0, System.currentTimeMillis() - started));
            } catch (RuntimeException exception) {
                future.complete(new CommandResult(true, false, exception.getMessage() == null ? "command failed" : exception.getMessage(), 1, System.currentTimeMillis() - started));
            }
        });
        return future;
    }

    private ServerPlayer findPlayer(String name) {
        if (name == null) {
            return null;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getGameProfile().name().equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }
}
