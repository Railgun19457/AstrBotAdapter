package io.github.railgun19457.astrbotadapter.platform.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformCommandService;

import java.util.concurrent.CompletionStage;

final class VelocityCommandService implements PlatformCommandService {
    private final ProxyServer proxy;

    VelocityCommandService(ProxyServer proxy) {
        this.proxy = proxy;
    }

    @Override
    public CompletionStage<CommandResult> execute(CommandExecutionRequest request) {
        long started = System.currentTimeMillis();
        return proxy.getCommandManager().executeAsync(proxy.getConsoleCommandSource(), request.command())
            .thenApply(success -> new CommandResult(true, success, success ? "command dispatched" : "command failed", success ? 0 : 1, System.currentTimeMillis() - started));
    }
}
