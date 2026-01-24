package io.github.railgun19457.astrbotadapter.communication.rest.controller;

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
import java.util.List;
import java.util.logging.Logger;

/**
 * 指令执行控制器
 */
public class CommandController {

    private final PlatformAdapter platformAdapter;
    private final PluginConfig config;
    private final Logger logger;

    public CommandController(PlatformAdapter platformAdapter, PluginConfig config, Logger logger) {
        this.platformAdapter = platformAdapter;
        this.config = config;
        this.logger = logger;
    }

    /**
     * 注册路由
     */
    public void registerRoutes(HttpRequestDispatcher dispatcher) {
        dispatcher.registerRoute("/api/command/execute", this::executeCommand);
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
        try {
            boolean success = platformAdapter.executeCommand(command);
            
            JsonObject data = new JsonObject();
            data.addProperty("command", command);
            data.addProperty("success", success);

            if (success) {
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

        String cmdName = command.split(" ")[0].toLowerCase();

        if ("WHITELIST".equals(filterMode)) {
            // 白名单模式：只允许列表中的指令
            return filterList.stream().anyMatch(c -> c.equalsIgnoreCase(cmdName));
        } else if ("BLACKLIST".equals(filterMode)) {
            // 黑名单模式：禁止列表中的指令
            return filterList.stream().noneMatch(c -> c.equalsIgnoreCase(cmdName));
        }

        return true;
    }
}
