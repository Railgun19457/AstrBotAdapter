package io.github.railgun19457.astrbotadapter.platform.bukkit;

import io.github.railgun19457.astrbotadapter.platform.spi.PlatformLogService;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformScheduler;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BukkitLogService implements PlatformLogService {
    private static final Pattern BUKKIT_LINE = Pattern.compile("^\\[(\\d{2}):(\\d{2}):(\\d{2})\\s+([^]]+)](?:\\s*:\\s*)?(.*)$");

    private final Plugin plugin;
    private final PlatformScheduler scheduler;

    public BukkitLogService(Plugin plugin, PlatformScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    @Override
    public CompletionStage<LogQueryResult> query(LogQuery query) {
        return scheduler.callAsync(() -> {
            Path root = plugin.getDataFolder().toPath().getParent().getParent();
            Path latest = root.resolve("logs").resolve("latest.log");
            if (!Files.exists(latest)) {
                return new LogQueryResult(false, List.of(new LogLine(System.currentTimeMillis(), "WARN", "latest.log not found")));
            }
            try {
                List<String> lines = Files.readAllLines(latest);
                int tail = query.tailLines() == null ? lines.size() : Math.min(lines.size(), Math.max(1, query.tailLines()));
                List<LogLine> selected = lines.subList(Math.max(0, lines.size() - tail), lines.size()).stream()
                    .map(this::parseLine)
                    .filter(line -> matchesLevel(line, query.level()))
                    .filter(line -> query.keyword() == null || query.keyword().isBlank() || line.message().contains(query.keyword()))
                    .filter(line -> query.since() == null || line.ts() >= query.since())
                    .filter(line -> query.until() == null || line.ts() <= query.until())
                    .toList();
                int max = Math.max(1, query.maxLines());
                boolean truncated = selected.size() > max;
                if (truncated) {
                    selected = selected.subList(selected.size() - max, selected.size());
                }
                return new LogQueryResult(truncated, selected);
            } catch (IOException exception) {
                return new LogQueryResult(false, List.of(new LogLine(System.currentTimeMillis(), "ERROR", "failed to read latest.log: " + exception.getMessage())));
            }
        });
    }

    private LogLine parseLine(String line) {
        Matcher matcher = BUKKIT_LINE.matcher(line);
        if (!matcher.matches()) {
            return new LogLine(System.currentTimeMillis(), "INFO", line);
        }
        LocalTime time = LocalTime.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
        long ts = LocalDateTime.of(LocalDate.now(), time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        String level = matcher.group(4).trim().split("\\s+")[0].toUpperCase();
        String message = matcher.group(5).isBlank() ? line : matcher.group(5);
        return new LogLine(ts, level, message);
    }

    private boolean matchesLevel(LogLine line, String level) {
        return level == null || level.isBlank() || line.level().equalsIgnoreCase(level);
    }
}
