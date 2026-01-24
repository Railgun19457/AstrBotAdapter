package io.github.railgun19457.astrbotadapter.core.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 占位符处理工具
 * 用于处理消息中的占位符替换
 */
public class PlaceholderUtil {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)}");

    /**
     * 替换消息中的占位符
     * @param message 原始消息
     * @param replacements 替换映射
     * @return 替换后的消息
     */
    public static String replace(String message, Map<String, String> replacements) {
        if (message == null || replacements == null || replacements.isEmpty()) {
            return message;
        }

        String result = message;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    /**
     * 替换消息中的占位符（使用可变参数）
     * @param message 原始消息
     * @param replacements 替换参数 (key1, value1, key2, value2, ...)
     * @return 替换后的消息
     */
    public static String replace(String message, Object... replacements) {
        if (message == null || replacements == null || replacements.length < 2) {
            return message;
        }

        String result = message;
        for (int i = 0; i < replacements.length - 1; i += 2) {
            String key = String.valueOf(replacements[i]);
            String value = String.valueOf(replacements[i + 1]);
            result = result.replace("{" + key + "}", value);
        }
        return result;
    }

    /**
     * 替换消息中的位置占位符 {0}, {1}, {2} ...
     * @param message 原始消息
     * @param args 位置参数
     * @return 替换后的消息
     */
    public static String format(String message, Object... args) {
        if (message == null || args == null || args.length == 0) {
            return message;
        }

        String result = message;
        for (int i = 0; i < args.length; i++) {
            result = result.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return result;
    }

    /**
     * 提取消息中的所有占位符名称
     * @param message 消息
     * @return 占位符名称数组
     */
    public static String[] extractPlaceholders(String message) {
        if (message == null) {
            return new String[0];
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(message);
        java.util.List<String> placeholders = new java.util.ArrayList<>();
        
        while (matcher.find()) {
            placeholders.add(matcher.group(1));
        }
        
        return placeholders.toArray(new String[0]);
    }

    /**
     * 构建占位符映射的Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 占位符映射构建器
     */
    public static class Builder {
        private final Map<String, String> map = new HashMap<>();

        public Builder put(String key, Object value) {
            map.put(key, String.valueOf(value));
            return this;
        }

        public Builder putIfNotNull(String key, Object value) {
            if (value != null) {
                map.put(key, String.valueOf(value));
            }
            return this;
        }

        public Map<String, String> build() {
            return new HashMap<>(map);
        }

        public String apply(String message) {
            return replace(message, map);
        }
    }

    /**
     * 颜色代码转换（& -> §）
     */
    public static String translateColorCodes(String message) {
        if (message == null) {
            return null;
        }
        return message.replace("&", "§");
    }

    /**
     * 去除颜色代码
     */
    public static String stripColorCodes(String message) {
        if (message == null) {
            return null;
        }
        return message.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }
}
