package io.github.railgun19457.astrbotadapter.core.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared log file reader utility.
 * Eliminates duplicated log reading code across platform adapters.
 */
public final class LogReader {

    private static final Pattern TIME_PATTERN = Pattern.compile("^(?:\\[)?(\\d{2}:\\d{2}:\\d{2})(?:\\])?");
    private static final Path DEFAULT_LOG_FILE = Path.of("logs", "latest.log");

    private LogReader() {}

    /**
     * Read the last N lines from the default log file.
     */
    public static List<String> getRecentLogs(int lines) {
        return getRecentLogs(DEFAULT_LOG_FILE, lines);
    }

    /**
     * Read the last N lines from the given log file.
     */
    public static List<String> getRecentLogs(Path logFile, int lines) {
        if (!Files.exists(logFile)) {
            return List.of();
        }
        try {
            List<String> allLines = Files.readAllLines(logFile);
            int start = Math.max(0, allLines.size() - lines);
            return new ArrayList<>(allLines.subList(start, allLines.size()));
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Filter log entries by time range from the default log file.
     */
    public static List<String> getLogsByTimeRange(long startTime, long endTime) {
        return getLogsByTimeRange(DEFAULT_LOG_FILE, startTime, endTime);
    }

    /**
     * Filter log entries by time range from the given log file.
     */
    public static List<String> getLogsByTimeRange(Path logFile, long startTime, long endTime) {
        if (!Files.exists(logFile) || startTime <= 0 || endTime <= 0 || endTime < startTime) {
            return List.of();
        }

        List<String> logs = new ArrayList<>();
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate baseDate = Instant.ofEpochMilli(startTime).atZone(zoneId).toLocalDate();

        try (BufferedReader reader = Files.newBufferedReader(logFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = TIME_PATTERN.matcher(line);
                if (!matcher.find()) {
                    continue;
                }
                LocalTime time = LocalTime.parse(matcher.group(1));
                LocalDateTime dateTime = LocalDateTime.of(baseDate, time);
                long ts = dateTime.atZone(zoneId).toInstant().toEpochMilli();
                // Handle midnight wraparound
                if (ts < startTime - 12 * 60 * 60 * 1000L) {
                    ts = dateTime.plusDays(1).atZone(zoneId).toInstant().toEpochMilli();
                }
                if (ts >= startTime && ts <= endTime) {
                    logs.add(line);
                }
            }
        } catch (IOException e) {
            // Return whatever we have so far
        }
        return logs;
    }

    /**
     * Format a duration in milliseconds to a human-readable string like "2d 3h 15m 42s".
     */
    public static String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        long remainingSeconds = seconds % 60;
        long remainingMinutes = minutes % 60;
        long remainingHours = hours % 24;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0 || days > 0) {
            sb.append(remainingHours).append("h ");
        }
        sb.append(remainingMinutes).append("m ");
        sb.append(remainingSeconds).append("s");
        return sb.toString().trim();
    }
}
