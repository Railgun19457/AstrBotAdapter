package io.github.railgun19457.astrbotadapter.core.util;

import io.github.railgun19457.astrbotadapter.core.config.PluginConfig;

import java.util.List;

/**
 * Shared command filtering utility.
 * Eliminates duplicated isCommandAllowed/matchCommandPattern logic across
 * AstrbotAdapterPlugin and CommandController.
 */
public final class CommandFilter {

    public static final int COMMAND_LOG_LIMIT = 200;
    public static final long COMMAND_LOG_CAPTURE_BUFFER_MS = 1500;

    private CommandFilter() {}

    /**
     * Check whether a command is allowed by the configured filter mode.
     */
    public static boolean isCommandAllowed(String command, PluginConfig config) {
        String filterMode = config.getCommandFilterMode();
        List<String> filterList = config.getCommandFilterList();

        if (filterMode == null || "NONE".equalsIgnoreCase(filterMode)
                || filterList == null || filterList.isEmpty()) {
            return true;
        }

        if ("WHITELIST".equalsIgnoreCase(filterMode)) {
            return filterList.stream().anyMatch(pattern -> matchCommandPattern(pattern, command));
        }

        if ("BLACKLIST".equalsIgnoreCase(filterMode)) {
            return filterList.stream().noneMatch(pattern -> matchCommandPattern(pattern, command));
        }

        return true;
    }

    /**
     * Check whether a command matches a pattern (supports * wildcard).
     */
    public static boolean matchCommandPattern(String pattern, String command) {
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }
        String regex = pattern.replace("*", ".*");
        return command.matches("(?i)" + regex);
    }

    /**
     * Collect command output logs from the platform adapter within a time window.
     */
    public static List<String> collectCommandLogs(
            io.github.railgun19457.astrbotadapter.platform.PlatformAdapter platformAdapter,
            long startTime, long endTime) {
        if (platformAdapter == null) {
            return List.of();
        }

        long from = Math.max(0, startTime - COMMAND_LOG_CAPTURE_BUFFER_MS);
        long to = endTime + COMMAND_LOG_CAPTURE_BUFFER_MS;
        List<String> logs;
        try {
            logs = platformAdapter.getLogsByTimeRange(from, to);
        } catch (Exception e) {
            return List.of();
        }

        if (logs == null || logs.isEmpty()) {
            return List.of();
        }

        int start = Math.max(0, logs.size() - COMMAND_LOG_LIMIT);
        return new java.util.ArrayList<>(logs.subList(start, logs.size()));
    }
}
