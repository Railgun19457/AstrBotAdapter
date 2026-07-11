package io.github.railgun19457.astrbotadapter.platform.spi;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface PlatformPlayerService {
    CompletionStage<List<PlayerSummary>> onlinePlayers(boolean includeDetails);

    CompletionStage<Optional<PlayerDetail>> findPlayer(PlayerQuery query, boolean includeHistory);
}
