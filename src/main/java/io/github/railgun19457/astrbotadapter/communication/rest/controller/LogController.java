package io.github.railgun19457.astrbotadapter.communication.rest.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.railgun19457.astrbotadapter.communication.protocol.ErrorCode;
import io.github.railgun19457.astrbotadapter.communication.protocol.Response;
import io.github.railgun19457.astrbotadapter.communication.rest.HttpRequestDispatcher;
import io.github.railgun19457.astrbotadapter.core.config.PluginConfig;
import io.github.railgun19457.astrbotadapter.platform.PlatformAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 日志查询控制器
 */
public class LogController {

    private final PlatformAdapter platformAdapter;
    private final PluginConfig config;

    public LogController(PlatformAdapter platformAdapter, PluginConfig config) {
        this.platformAdapter = platformAdapter;
        this.config = config;
    }

    /**
     * 注册路由
     */
    public void registerRoutes(HttpRequestDispatcher dispatcher) {
        dispatcher.registerRoute("/api/v1/logs", this::getLogs);
    }

    /**
     * 获取日志
     */
    private Response getLogs(FullHttpRequest request) {
        if (request.method() != HttpMethod.GET) {
            return Response.error(ErrorCode.REQUEST_PARAM_ERROR, "仅支持GET请求");
        }

        if (!config.isLogQueryEnabled()) {
            return Response.error(ErrorCode.FEATURE_DISABLED, "日志查询功能已禁用");
        }

        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        
        // 获取参数
        int lines = getIntParam(decoder, "lines", 100);
        long startTime = getLongParam(decoder, "startTime", 0);
        long endTime = getLongParam(decoder, "endTime", 0);
        String level = getStringParam(decoder, "level", null);
        String keyword = getStringParam(decoder, "keyword", null);

        // 限制最大行数
        int maxLines = config.getLogQueryMaxLines();
        if (maxLines > 0 && lines > maxLines) {
            lines = maxLines;
        }

        final int effectiveLines = lines;
        final long effectiveStartTime = startTime;
        final long effectiveEndTime = endTime;
        final String effectiveLevel = level;
        final String effectiveKeyword = keyword;

        JsonObject data = runOnServerThread(() -> {
            List<String> logs;
            if (effectiveStartTime > 0 && effectiveEndTime > 0) {
                logs = platformAdapter.getLogsByTimeRange(effectiveStartTime, effectiveEndTime);
            } else {
                logs = platformAdapter.getRecentLogs(effectiveLines);
            }

            JsonArray logArray = new JsonArray();

            for (String log : logs) {
                if (!matchLevel(log, effectiveLevel)) {
                    continue;
                }
                if (!matchKeyword(log, effectiveKeyword)) {
                    continue;
                }

                JsonObject entry = new JsonObject();
                entry.addProperty("server", platformAdapter.getServerName());
                entry.addProperty("scope", platformAdapter.getPlatformType().isProxy() ? "proxy" : "local");
                entry.addProperty("message", log);
                logArray.add(entry);
            }

            JsonObject payload = new JsonObject();
            payload.addProperty("count", logArray.size());
            payload.add("logs", logArray);
            return payload;
        });

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

    private String getStringParam(QueryStringDecoder decoder, String key, String defaultValue) {
        List<String> values = decoder.parameters().get(key);
        if (values != null && !values.isEmpty()) {
            String value = values.get(0);
            return value == null || value.isBlank() ? defaultValue : value;
        }
        return defaultValue;
    }

    private boolean matchLevel(String log, String level) {
        if (level == null || level.isBlank()) {
            return true;
        }
        String normalized = level.toUpperCase(Locale.ROOT);
        String line = log == null ? "" : log.toUpperCase(Locale.ROOT);
        return line.contains("[" + normalized + "]") || line.contains(" " + normalized + " ");
    }

    private boolean matchKeyword(String log, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        if (log == null) {
            return false;
        }
        return log.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private <T> T runOnServerThread(Supplier<T> supplier) {
        if (platformAdapter.getScheduler().isMainThread()) {
            return supplier.get();
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        platformAdapter.getScheduler().runSync(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });

        try {
            return future.get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("获取日志数据失败", e);
        }
    }
}
