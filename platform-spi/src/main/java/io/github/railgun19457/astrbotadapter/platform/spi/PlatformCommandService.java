package io.github.railgun19457.astrbotadapter.platform.spi;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

public interface PlatformCommandService {
    CompletionStage<CommandResult> execute(CommandExecutionRequest request);

    record CommandExecutionRequest(String command, String executorType, String playerName, Duration executionTimeout) {
    }

    record CommandResult(boolean accepted, boolean executed, String output, int exitCode, long durationMillis) {
    }
}
