package io.github.railgun19457.astrbotadapter.platform.fabric;

import io.github.railgun19457.astrbotadapter.platform.spi.PlatformCommandService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

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
            ServerCommandSource source = server.getCommandSource();
            if ("player".equalsIgnoreCase(request.executorType())) {
                ServerPlayerEntity player = findPlayer(request.playerName());
                if (player == null) {
                    future.complete(new CommandResult(false, false, "player not online", 1, System.currentTimeMillis() - started));
                    return;
                }
                source = player.getCommandSource();
            }
            int result = server.getCommandManager().executeWithPrefix(source, request.command());
            boolean executed = result > 0;
            future.complete(new CommandResult(true, executed, executed ? "command dispatched" : "command failed", executed ? 0 : 1, System.currentTimeMillis() - started));
        });
        return future;
    }

    private ServerPlayerEntity findPlayer(String name) {
        if (name == null) {
            return null;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getGameProfile().getName().equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }
}
