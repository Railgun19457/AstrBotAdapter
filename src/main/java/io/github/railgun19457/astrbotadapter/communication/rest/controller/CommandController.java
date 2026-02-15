package io.github.railgun19457.astrbotadapter.communication.rest.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.railgun19457.astrbotadapter.communication.protocol.ErrorCode;
import io.github.railgun19457.astrbotadapter.communication.protocol.Response;
import io.github.railgun19457.astrbotadapter.communication.proxy.ProxyBridgeProvider;
import io.github.railgun19457.astrbotadapter.communication.rest.HttpRequestDispatcher;
import io.github.railgun19457.astrbotadapter.core.config.PluginConfig;
import io.github.railgun19457.astrbotadapter.core.util.JsonUtil;
import io.github.railgun19457.astrbotadapter.platform.PlatformAdapter;
import io.github.railgun19457.astrbotadapter.service.command.CommandExecutionService;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;

import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * 指令执行控制器
 * On Velocity with proxy bridge, supports targetServer parameter to route commands.
 */
public class CommandController {

    private final PlatformAdapter platformAdapter;
    private final PluginConfig config;
    private final ProxyBridgeProvider proxyBridge; // nullable
    private final Logger logger;

    public CommandController(PlatformAdapter platformAdapter, PluginConfig config,
                             ProxyBridgeProvider proxyBridge, Logger logger) {
        this.platformAdapter = platformAdapter;
        this.config = config;
        this.proxyBridge = proxyBridge;
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
        CommandExecutionService.ValidationResult validation =
                CommandExecutionService.validateAndNormalizeCommand(command, config);
        if (!validation.isValid()) {
            return Response.error(validation.errorCode(), validation.detail());
        }
        command = validation.command();

        // Check if command should be routed to a backend server
        String targetServer = JsonUtil.getString(params, "targetServer", null);
        if (targetServer != null) {
            targetServer = targetServer.trim();
        }
        if (targetServer != null && !targetServer.isEmpty() && proxyBridge != null) {
            return routeCommandToBackend(targetServer, command);
        }

        // 执行指令（本地）
        CommandExecutionService.ExecutionResult result = CommandExecutionService.executeLocalCommand(
                platformAdapter,
                command,
                CommandExecutionService.EXECUTOR_CONSOLE,
                null,
                logger
        );

        if (!result.success()) {
            return Response.error(
                    result.errorCode() == null ? ErrorCode.COMMAND_EXECUTE_FAILED : result.errorCode(),
                    result.errorMessage() == null ? "指令执行失败" : result.errorMessage()
            );
        }

        JsonObject data = new JsonObject();
        data.addProperty("command", result.command());
        data.addProperty("success", true);
        data.addProperty("executionTime", result.executionTime());

        if (!result.logs().isEmpty()) {
            JsonArray logArray = new JsonArray();
            for (String log : result.logs()) {
                logArray.add(log);
            }
            data.add("logs", logArray);
            data.addProperty("output", String.join("\n", result.logs()));
        } else {
            data.addProperty("output", "Command executed");
        }

        logger.info("外部指令执行成功: " + command);
        return Response.success(data);
    }

    /**
     * Route a command to a specific backend server via proxy bridge.
     * The command is sent asynchronously via PMC; this returns an immediate acknowledgement.
     */
    private Response routeCommandToBackend(String serverName, String command) {
        boolean sent = proxyBridge.sendCommandToBackend(
                serverName, command, "CONSOLE", null, null);

        JsonObject data = new JsonObject();
        data.addProperty("command", command);
        data.addProperty("targetServer", serverName);

        if (sent) {
            data.addProperty("success", true);
            data.addProperty("output", "Command sent to backend: " + serverName);
            logger.info("指令已发送到后端服务器: " + serverName + " -> " + command);
            return Response.success(data);
        } else {
            data.addProperty("success", false);
            return Response.error(ErrorCode.COMMAND_EXECUTE_FAILED,
                    "无法将指令发送到后端服务器: " + serverName + " (未连接或无在线玩家)");
        }
    }

}
