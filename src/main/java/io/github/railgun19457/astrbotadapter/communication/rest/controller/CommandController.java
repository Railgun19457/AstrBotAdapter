package io.github.railgun19457.astrbotadapter.communication.rest.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.railgun19457.astrbotadapter.communication.protocol.ErrorCode;
import io.github.railgun19457.astrbotadapter.communication.protocol.Response;
import io.github.railgun19457.astrbotadapter.communication.rest.HttpRequestDispatcher;
import io.github.railgun19457.astrbotadapter.core.config.PluginConfig;
import io.github.railgun19457.astrbotadapter.core.util.JsonUtil;
import io.github.railgun19457.astrbotadapter.platform.PlatformAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * 指令执行控制器
 */
public class CommandController {

    private final PlatformAdapter platformAdapter;
    private final PluginConfig config;
    private final Logger logger;

    private static final int COMMAND_LOG_LIMIT = 200;
    private static final long COMMAND_LOG_CAPTURE_BUFFER_MS = 1500;

    public CommandController(PlatformAdapter platformAdapter, PluginConfig config, Logger logger) {
        this.platformAdapter = platformAdapter;
        this.config = config;
        this.logger = logger;
    }

    /**
     * 注册路由
     */
    public void registerRoutes(HttpRequestDispatcher dispatcher) {
        dispatcher.registerRoute("/api/v1/command/execute", this::executeCommand);
    }

    /**
     * 执行指令
     */
    private Response executeCommand(FullHttpRequest request) {
        if (request.method() != HttpMethod.POST) {
            return Response.error(ErrorCode.REQUEST_PARAM_ERROR, "仅支持POST请求");
        }

        // 检查功能是否启用
        if (!config.isCommandEnabled()) {
            return Response.error(ErrorCode.FEATURE_DISABLED, "外部指令执行功能已禁用");
        }

        // 解析请求体
        String body = request.content().toString(StandardCharsets.UTF_8);
        JsonObject params;
        try {
            params = JsonUtil.parseObject(body);
        } catch (Exception e) {
            return Response.error(ErrorCode.REQUEST_FORMAT_ERROR, "请求体JSON格式错误");
        }

        String command = JsonUtil.getString(params, "command", null);
        if (command == null || command.isEmpty()) {
            return Response.error(ErrorCode.REQUEST_PARAM_MISSING, "缺少command参数");
        }

        // 移除命令前缀
        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        // 指令过滤
        if (!isCommandAllowed(command)) {
            logger.warning("指令被过滤: " + command);
            return Response.error(ErrorCode.COMMAND_FILTERED, "指令被过滤: " + command);
        }

        // 执行指令
        long startTime = System.currentTimeMillis();
        try {
            boolean success = platformAdapter.executeCommand(command);
            long endTime = System.currentTimeMillis();
            
            JsonObject data = new JsonObject();
            data.addProperty("command", command);
            data.addProperty("success", success);
            data.addProperty("executionTime", endTime - startTime);

            if (success) {
                List<String> logs = collectCommandLogs(startTime, endTime);
                if (!logs.isEmpty()) {
                    JsonArray logArray = new JsonArray();
                    for (String log : logs) {
                        logArray.add(log);
                    }
                    data.add("logs", logArray);
                    data.addProperty("output", String.join("\n", logs));
                } else {
                    data.addProperty("output", "Command executed");
                }
                logger.info("外部指令执行成功: " + command);
                return Response.success(data);
            } else {
                return Response.error(ErrorCode.COMMAND_EXECUTE_FAILED, "指令执行失败");
            }
        } catch (Exception e) {
            logger.warning("指令执行异常: " + command + " - " + e.getMessage());
            return Response.error(ErrorCode.COMMAND_EXECUTE_FAILED, e.getMessage());
        }
    }

    /**
     * 检查指令是否被允许
     */
    private boolean isCommandAllowed(String command) {
        String filterMode = config.getCommandFilterMode();
        List<String> filterList = config.getCommandFilterList();

        if ("NONE".equals(filterMode) || filterList == null || filterList.isEmpty()) {
            return true;
        }

        if ("WHITELIST".equals(filterMode)) {
            // 白名单模式：只允许列表中的指令
            return filterList.stream().anyMatch(pattern -> matchCommandPattern(pattern, command));
        } else if ("BLACKLIST".equals(filterMode)) {
            // 黑名单模式：禁止列表中的指令
            return filterList.stream().noneMatch(pattern -> matchCommandPattern(pattern, command));
        }

        return true;
    }

    private boolean matchCommandPattern(String pattern, String command) {
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }
        String regex = pattern.replace("*", ".*");
        return command.matches("(?i)" + regex);
    }

    private List<String> collectCommandLogs(long startTime, long endTime) {
        if (platformAdapter == null) {
            return List.of();
        }

        long from = Math.max(0, startTime - COMMAND_LOG_CAPTURE_BUFFER_MS);
        long to = endTime + COMMAND_LOG_CAPTURE_BUFFER_MS;
        List<String> logs;
        try {
            logs = platformAdapter.getLogsByTimeRange(from, to);
        } catch (Exception e) {
            logger.warning("获取指令日志失败: " + e.getMessage());
            return List.of();
        }

        if (logs == null || logs.isEmpty()) {
            return List.of();
        }

        int start = Math.max(0, logs.size() - COMMAND_LOG_LIMIT);
        return new ArrayList<>(logs.subList(start, logs.size()));
    }
}
