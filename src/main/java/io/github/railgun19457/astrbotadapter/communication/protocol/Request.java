package io.github.railgun19457.astrbotadapter.communication.protocol;

import com.google.gson.JsonObject;
import io.github.railgun19457.astrbotadapter.core.util.JsonUtil;

/**
 * REST API 请求格式
 */
public class Request {

    private String action;
    private JsonObject params;
    private long timestamp;

    public Request() {
        this.timestamp = System.currentTimeMillis();
    }

    public Request(String action) {
        this();
        this.action = action;
    }

    public Request(String action, JsonObject params) {
        this(action);
        this.params = params;
    }

    // Getters and Setters
    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public JsonObject getParams() {
        return params;
    }

    public void setParams(JsonObject params) {
        this.params = params;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * 获取参数字符串值
     */
    public String getParamString(String key) {
        return getParamString(key, null);
    }

    /**
     * 获取参数字符串值（带默认值）
     */
    public String getParamString(String key, String defaultValue) {
        if (params == null || !params.has(key) || params.get(key).isJsonNull()) {
            return defaultValue;
        }
        return params.get(key).getAsString();
    }

    /**
     * 获取参数整数值
     */
    public int getParamInt(String key, int defaultValue) {
        if (params == null || !params.has(key) || params.get(key).isJsonNull()) {
            return defaultValue;
        }
        return params.get(key).getAsInt();
    }

    /**
     * 获取参数长整数值
     */
    public long getParamLong(String key, long defaultValue) {
        if (params == null || !params.has(key) || params.get(key).isJsonNull()) {
            return defaultValue;
        }
        return params.get(key).getAsLong();
    }

    /**
     * 获取参数布尔值
     */
    public boolean getParamBoolean(String key, boolean defaultValue) {
        if (params == null || !params.has(key) || params.get(key).isJsonNull()) {
            return defaultValue;
        }
        return params.get(key).getAsBoolean();
    }

    /**
     * 转换为JSON字符串
     */
    public String toJson() {
        return JsonUtil.toJsonCompact(this);
    }

    /**
     * 从JSON字符串解析
     */
    public static Request fromJson(String json) {
        return JsonUtil.fromJson(json, Request.class);
    }
}
