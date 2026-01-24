package io.github.railgun19457.astrbotadapter.communication.rest.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.railgun19457.astrbotadapter.communication.protocol.ErrorCode;
import io.github.railgun19457.astrbotadapter.communication.protocol.Response;
import io.github.railgun19457.astrbotadapter.communication.rest.HttpRequestDispatcher;
import io.github.railgun19457.astrbotadapter.platform.PlatformAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.util.List;

/**
 * 日志查询控制器
 */
public class LogController {

    private final PlatformAdapter platformAdapter;

    public LogController(PlatformAdapter platformAdapter) {
        this.platformAdapter = platformAdapter;
    }

    /**
     * 注册路由
     */
    public void registerRoutes(HttpRequestDispatcher dispatcher) {
        dispatcher.registerRoute("/api/logs", this::getLogs);
    }

    /**
     * 获取日志
     */
    private Response getLogs(FullHttpRequest request) {
        if (request.method() != HttpMethod.GET) {
            return Response.error(ErrorCode.REQUEST_PARAM_ERROR, "仅支持GET请求");
        }

        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        
        // 获取参数
        int lines = getIntParam(decoder, "lines", 100);
        long startTime = getLongParam(decoder, "startTime", 0);
        long endTime = getLongParam(decoder, "endTime", 0);

        // 限制最大行数
        if (lines > 1000) {
            lines = 1000;
        }

        List<String> logs;
        
        if (startTime > 0 && endTime > 0) {
            // 按时间范围查询
            logs = platformAdapter.getLogsByTimeRange(startTime, endTime);
        } else {
            // 按行数查询
            logs = platformAdapter.getRecentLogs(lines);
        }

        JsonArray logArray = new JsonArray();
        for (String log : logs) {
            logArray.add(log);
        }

        JsonObject data = new JsonObject();
        data.addProperty("count", logs.size());
        data.add("logs", logArray);

        return Response.success(data);
    }

    private int getIntParam(QueryStringDecoder decoder, String key, int defaultValue) {
        List<String> values = decoder.parameters().get(key);
        if (values != null && !values.isEmpty()) {
            try {
                return Integer.parseInt(values.get(0));
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    private long getLongParam(QueryStringDecoder decoder, String key, long defaultValue) {
        List<String> values = decoder.parameters().get(key);
        if (values != null && !values.isEmpty()) {
            try {
                return Long.parseLong(values.get(0));
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }
}
