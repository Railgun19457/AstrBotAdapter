package io.github.railgun19457.astrbotadapter.protocol.payload;

import io.github.railgun19457.astrbotadapter.protocol.ServerRole;
import io.github.railgun19457.astrbotadapter.protocol.Status;

import java.util.List;

public final class LogPayloads {
    private LogPayloads() {
    }

    public record LogQueryRequest(Integer tailLines, Long since, Long until, String level, String keyword, int maxLines) {
    }

    public record LogQueryResponse(List<LogServerItem> servers, List<CommonPayloads.AggregateErrorItem> errors, long generatedAt) {
    }

    public record LogServerItem(String serverId, ServerRole role, Status status, boolean truncated, List<LogLine> lines) {
    }

    public record LogLine(long ts, String level, String message) {
    }
}
