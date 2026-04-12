package io.github.railgun19457.astrbotadapter.service.command;

import io.github.railgun19457.astrbotadapter.communication.protocol.ErrorCode;
import io.github.railgun19457.astrbotadapter.core.config.PluginConfig;
import io.github.railgun19457.astrbotadapter.core.util.CommandFilter;
import io.github.railgun19457.astrbotadapter.platform.PlatformAdapter;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Shared command validation and execution service.
 * Used by both WebSocket and REST command entrypoints.
 */
public final class CommandExecutionService {

    public static final String EXECUTOR_CONSOLE = "CONSOLE";
    public static final String EXECUTOR_PLAYER = "PLAYER";

    private CommandExecutionService() {
    }

    public static ValidationResult validateAndNormalizeCommand(String rawCommand, PluginConfig config) {
        if (rawCommand == null) {
            return ValidationResult.error(ErrorCode.REQUEST_PARAM_MISSING, "缺少command参数");
        }

        String command = rawCommand.trim();
        if (command.isEmpty()) {
            return ValidationResult.error(ErrorCode.REQUEST_PARAM_MISSING, "缺少command参数");
        }

        if (command.startsWith("/")) {
            command = command.substring(1).trim();
        }
        if (command.isEmpty()) {
            return ValidationResult.error(ErrorCode.REQUEST_PARAM_MISSING, "缺少command参数");
        }

        if (!CommandFilter.isCommandAllowed(command, config)) {
            return ValidationResult.error(ErrorCode.COMMAND_FILTERED, "指令被过滤: " + command);
        }

        return ValidationResult.success(command);
    }

    public static ExecutionResult executeLocalCommand(
            PlatformAdapter platformAdapter,
            String command,
            String executor,
            String playerUuid,
            Logger logger
    ) {
        long startTime = System.currentTimeMillis();
        long endTime;

        if (platformAdapter == null) {
            endTime = System.currentTimeMillis();
            return ExecutionResult.failure(
                    command,
                    startTime,
                    endTime,
                    ErrorCode.SERVER_UNAVAILABLE,
                    "平台适配器不可用"
            );
        }

        try {
            boolean success;
            if (EXECUTOR_PLAYER.equalsIgnoreCase(executor)) {
                if (playerUuid == null || playerUuid.isBlank()) {
                    endTime = System.currentTimeMillis();
                    return ExecutionResult.failure(
                            command,
                            startTime,
                            endTime,
                            ErrorCode.REQUEST_PARAM_MISSING,
                            "缺少playerUuid参数"
                    );
                }

                UUID uuid;
                try {
                    uuid = UUID.fromString(playerUuid.trim());
                } catch (IllegalArgumentException ex) {
                    endTime = System.currentTimeMillis();
                    return ExecutionResult.failure(
                            command,
                            startTime,
                            endTime,
                            ErrorCode.REQUEST_PARAM_ERROR,
                            "playerUuid格式错误"
                    );
                }

                var playerOpt = platformAdapter.getPlayer(uuid);
                if (playerOpt.isEmpty()) {
                    endTime = System.currentTimeMillis();
                    return ExecutionResult.failure(
                            command,
                            startTime,
                            endTime,
                            ErrorCode.PLAYER_NOT_ONLINE,
                            "玩家不在线: " + playerUuid
                    );
                }
                success = platformAdapter.executeCommand(playerOpt.get(), command);
            } else {
                success = platformAdapter.executeCommand(command);
            }

            endTime = System.currentTimeMillis();
            if (!success) {
                return ExecutionResult.failure(
                        command,
                        startTime,
                        endTime,
                        ErrorCode.COMMAND_EXECUTE_FAILED,
                        "指令执行失败"
                );
            }

            // Avoid sampling global logs here: they are often polluted by plugin self-logs
            // and can be misleading for command execution output.
            return ExecutionResult.success(command, startTime, endTime, List.of());
        } catch (Exception e) {
            endTime = System.currentTimeMillis();
            if (logger != null) {
                logger.warning("外部指令执行异常: " + e.getMessage());
            }
            return ExecutionResult.failure(
                    command,
                    startTime,
                    endTime,
                    ErrorCode.COMMAND_EXECUTE_FAILED,
                    "指令执行异常: " + e.getMessage()
            );
        }
    }

    public record ValidationResult(String command, ErrorCode errorCode, String detail) {
        public static ValidationResult success(String command) {
            return new ValidationResult(command, null, null);
        }

        public static ValidationResult error(ErrorCode errorCode, String detail) {
            return new ValidationResult(null, errorCode, detail);
        }

        public boolean isValid() {
            return errorCode == null;
        }
    }

    public record ExecutionResult(
            boolean success,
            String command,
            long startTime,
            long endTime,
            ErrorCode errorCode,
            String errorMessage,
            List<String> logs
    ) {
        public static ExecutionResult success(String command, long startTime, long endTime, List<String> logs) {
            return new ExecutionResult(true, command, startTime, endTime, null, null,
                    logs == null ? List.of() : logs);
        }

        public static ExecutionResult failure(String command, long startTime, long endTime,
                                              ErrorCode errorCode, String errorMessage) {
            return new ExecutionResult(false, command, startTime, endTime, errorCode, errorMessage, List.of());
        }

        public long executionTime() {
            return Math.max(0, endTime - startTime);
        }
    }
}