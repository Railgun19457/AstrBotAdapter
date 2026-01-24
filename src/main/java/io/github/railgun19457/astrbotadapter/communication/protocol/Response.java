package io.github.railgun19457.astrbotadapter.communication.protocol;

import com.google.gson.JsonObject;
import io.github.railgun19457.astrbotadapter.core.util.JsonUtil;

/**
 * REST API 响应格式
 */
public class Response {

    private int code;
    private String message;
    private Object data;
    private long timestamp;

    public Response() {
        this.timestamp = System.currentTimeMillis();
    }

    public Response(int code, String message) {
        this();
        this.code = code;
        this.message = message;
    }

    public Response(int code, String message, Object data) {
        this(code, message);
        this.data = data;
    }

    /**
     * 成功响应
     */
    public static Response success() {
        return new Response(ErrorCode.SUCCESS.getCode(), "success");
    }

    /**
     * 成功响应（带数据）
     */
    public static Response success(Object data) {
        return new Response(ErrorCode.SUCCESS.getCode(), "success", data);
    }

    /**
     * 错误响应
     */
    public static Response error(ErrorCode errorCode) {
        return new Response(errorCode.getCode(), errorCode.getMessage());
    }

    /**
     * 错误响应（自定义消息）
     */
    public static Response error(ErrorCode errorCode, String message) {
        return new Response(errorCode.getCode(), message);
    }

    /**
     * 错误响应（自定义错误码和消息）
     */
    public static Response error(int code, String message) {
        return new Response(code, message);
    }

    // Getters and Setters
    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * 判断是否成功
     */
    public boolean isSuccess() {
        return code == 0;
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
    public static Response fromJson(String json) {
        return JsonUtil.fromJson(json, Response.class);
    }
}
