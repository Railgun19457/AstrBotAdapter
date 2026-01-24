package io.github.railgun19457.astrbotadapter.core.util;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * JSON工具类
 * 提供JSON序列化和反序列化功能
 */
public class JsonUtil {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    private static final Gson GSON_COMPACT = new GsonBuilder()
            .disableHtmlEscaping()
            .create();

    /**
     * 对象转JSON字符串（格式化）
     */
    public static String toJson(Object obj) {
        return GSON.toJson(obj);
    }

    /**
     * 对象转JSON字符串（紧凑）
     */
    public static String toJsonCompact(Object obj) {
        return GSON_COMPACT.toJson(obj);
    }

    /**
     * JSON字符串转对象
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        return GSON.fromJson(json, clazz);
    }

    /**
     * JSON字符串转对象（泛型）
     */
    public static <T> T fromJson(String json, Type type) {
        return GSON.fromJson(json, type);
    }

    /**
     * JSON字符串转Map
     */
    public static Map<String, Object> toMap(String json) {
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        return GSON.fromJson(json, type);
    }

    /**
     * 对象转JsonObject
     */
    public static JsonObject toJsonObject(Object obj) {
        return GSON.toJsonTree(obj).getAsJsonObject();
    }

    /**
     * 解析JSON字符串为JsonObject
     */
    public static JsonObject parseObject(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    /**
     * 解析JSON字符串为JsonArray
     */
    public static JsonArray parseArray(String json) {
        return JsonParser.parseString(json).getAsJsonArray();
    }

    /**
     * 安全获取字符串字段
     */
    public static String getString(JsonObject json, String key, String defaultValue) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            return json.get(key).getAsString();
        }
        return defaultValue;
    }

    /**
     * 安全获取整数字段
     */
    public static int getInt(JsonObject json, String key, int defaultValue) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            return json.get(key).getAsInt();
        }
        return defaultValue;
    }

    /**
     * 安全获取长整数字段
     */
    public static long getLong(JsonObject json, String key, long defaultValue) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            return json.get(key).getAsLong();
        }
        return defaultValue;
    }

    /**
     * 安全获取布尔字段
     */
    public static boolean getBoolean(JsonObject json, String key, boolean defaultValue) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            return json.get(key).getAsBoolean();
        }
        return defaultValue;
    }

    /**
     * 安全获取JsonObject字段
     */
    public static JsonObject getObject(JsonObject json, String key) {
        if (json.has(key) && json.get(key).isJsonObject()) {
            return json.getAsJsonObject(key);
        }
        return null;
    }

    /**
     * 安全获取JsonArray字段
     */
    public static JsonArray getArray(JsonObject json, String key) {
        if (json.has(key) && json.get(key).isJsonArray()) {
            return json.getAsJsonArray(key);
        }
        return null;
    }

    /**
     * 检查JSON是否有效
     */
    public static boolean isValid(String json) {
        try {
            JsonParser.parseString(json);
            return true;
        } catch (JsonSyntaxException e) {
            return false;
        }
    }

    /**
     * 获取Gson实例
     */
    public static Gson getGson() {
        return GSON;
    }
}
