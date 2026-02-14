package io.github.railgun19457.astrbotadapter.communication.proxy;

import com.google.gson.JsonObject;
import io.github.railgun19457.astrbotadapter.core.util.JsonUtil;

import java.util.UUID;

/**
 * Message format for proxy-backend plugin messaging communication.
 * Serialized to JSON and transmitted via Plugin Messaging Channel.
 */
public class ProxyMessage {

    private ProxyMessageType type;
    private String id;
    private String replyTo;
    private String serverName;
    private JsonObject data;
    private long timestamp;

    public ProxyMessage() {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.timestamp = System.currentTimeMillis();
    }

    public ProxyMessage(ProxyMessageType type) {
        this();
        this.type = type;
    }

    public ProxyMessage(ProxyMessageType type, JsonObject data) {
        this(type);
        this.data = data;
    }

    // ===== Builder =====

    public static Builder builder() {
        return new Builder();
    }

    // ===== Serialization =====

    /**
     * Serialize to JSON string.
     */
    public String toJson() {
        return JsonUtil.toJsonCompact(this);
    }

    /**
     * Serialize to byte array for plugin messaging.
     */
    public byte[] toBytes() {
        return toJson().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Deserialize from JSON string.
     */
    public static ProxyMessage fromJson(String json) {
        return JsonUtil.fromJson(json, ProxyMessage.class);
    }

    /**
     * Deserialize from byte array.
     */
    public static ProxyMessage fromBytes(byte[] bytes) {
        String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        return fromJson(json);
    }

    // ===== Getters and Setters =====

    public ProxyMessageType getType() {
        return type;
    }

    public void setType(ProxyMessageType type) {
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public JsonObject getData() {
        return data;
    }

    public void setData(JsonObject data) {
        this.data = data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    // ===== Builder =====

    public static class Builder {
        private final ProxyMessage message;

        public Builder() {
            this.message = new ProxyMessage();
        }

        public Builder type(ProxyMessageType type) {
            message.setType(type);
            return this;
        }

        public Builder id(String id) {
            message.setId(id);
            return this;
        }

        public Builder replyTo(String replyTo) {
            message.setReplyTo(replyTo);
            return this;
        }

        public Builder serverName(String serverName) {
            message.setServerName(serverName);
            return this;
        }

        public Builder data(JsonObject data) {
            message.setData(data);
            return this;
        }

        public ProxyMessage build() {
            return message;
        }
    }
}
