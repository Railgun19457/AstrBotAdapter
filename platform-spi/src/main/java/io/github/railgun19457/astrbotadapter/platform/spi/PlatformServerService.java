package io.github.railgun19457.astrbotadapter.platform.spi;

import java.util.concurrent.CompletionStage;

public interface PlatformServerService {
    CompletionStage<ServerStatusItem> status(boolean includePerformance);

    ServerSnapshot snapshot();
}
