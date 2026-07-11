package io.github.railgun19457.astrbotadapter.platform.spi;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface PlatformLogService {
    CompletionStage<LogQueryResult> query(LogQuery query);

    record LogQuery(Integer tailLines, Long since, Long until, String level, String keyword, int maxLines) {
    }

    record LogQueryResult(boolean truncated, List<LogLine> lines) {
    }

    record LogLine(long ts, String level, String message) {
    }
}
