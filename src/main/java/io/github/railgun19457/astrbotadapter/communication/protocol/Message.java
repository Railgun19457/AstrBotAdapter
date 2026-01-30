package io.github.railgun19457.astrbotadapter.communication.protocol;

import com.google.gson.JsonObject;
import io.github.railgun19457.astrbotadapter.core.util.JsonUtil;

import java.util.UUID;

/**
 * 统一消息格式
 * 用于WebSocket通信的消息封装
 */
public class Message {

    private MessageType type;
    private String id;
    private String replyTo;
    private MessageSource source;
    private MessageTarget target;
    private JsonObject payload;
    private long timestamp;

    public Message() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
    }

    public Message(MessageType type) {
        this();
        this.type = type;
    }

    public Message(MessageType type, JsonObject payload) {
        this(type);
        this.payload = payload;
    }

    // Builder 模式
    public static Builder builder() {
        return new Builder();
    }

    // Getters and Setters
    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
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

    public MessageSource getSource() {
        return source;
    }

    public void setSource(MessageSource source) {
        this.source = source;
    }

    public MessageTarget getTarget() {
        return target;
    }

    public void setTarget(MessageTarget target) {
        this.target = target;
    }

    public JsonObject getPayload() {
        return payload;
    }

    public void setPayload(JsonObject payload) {
        this.payload = payload;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
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
    public static Message fromJson(String json) {
        return JsonUtil.fromJson(json, Message.class);
    }

    /**
     * 消息来源
     */
    public static class MessageSource {
        private String type; // PLAYER, SERVER, SYSTEM
        private ServerInfo server;
        private PlayerInfo player;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public ServerInfo getServer() {
            return server;
        }

        public void setServer(ServerInfo server) {
            this.server = server;
        }

        public PlayerInfo getPlayer() {
            return player;
        }

        public void setPlayer(PlayerInfo player) {
            this.player = player;
        }

        public static MessageSource player(PlayerInfo player, ServerInfo server) {
            MessageSource source = new MessageSource();
            source.setType("PLAYER");
            source.setPlayer(player);
            source.setServer(server);
            return source;
        }

        public static MessageSource server(ServerInfo server) {
            MessageSource source = new MessageSource();
            source.setType("SERVER");
            source.setServer(server);
            return source;
        }

        public static MessageSource system() {
            MessageSource source = new MessageSource();
            source.setType("SYSTEM");
            return source;
        }
    }

    /**
     * 消息目标
     */
    public static class MessageTarget {
        private String type; // PLAYER, BROADCAST, SERVER
        private String playerUuid;
        private String playerName;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getPlayerUuid() {
            return playerUuid;
        }

        public void setPlayerUuid(String playerUuid) {
            this.playerUuid = playerUuid;
        }

        public String getPlayerName() {
            return playerName;
        }

        public void setPlayerName(String playerName) {
            this.playerName = playerName;
        }

        public static MessageTarget player(String uuid, String name) {
            MessageTarget target = new MessageTarget();
            target.setType("PLAYER");
            target.setPlayerUuid(uuid);
            target.setPlayerName(name);
            return target;
        }

        public static MessageTarget broadcast() {
            MessageTarget target = new MessageTarget();
            target.setType("BROADCAST");
            return target;
        }

        public static MessageTarget server() {
            MessageTarget target = new MessageTarget();
            target.setType("SERVER");
            return target;
        }
    }

    /**
     * 服务器信息
     */
    public static class ServerInfo {
        private String name;
        private String platform;
        private String version;

        public ServerInfo() {}

        public ServerInfo(String name, String platform, String version) {
            this.name = name;
            this.platform = platform;
            this.version = version;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPlatform() {
            return platform;
        }

        public void setPlatform(String platform) {
            this.platform = platform;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }

    /**
     * 玩家信息
     */
    public static class PlayerInfo {
        private String uuid;
        private String name;
        private String displayName;

        public PlayerInfo() {}

        public PlayerInfo(String uuid, String name, String displayName) {
            this.uuid = uuid;
            this.name = name;
            this.displayName = displayName;
        }

        public String getUuid() {
            return uuid;
        }

        public void setUuid(String uuid) {
            this.uuid = uuid;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }

    /**
     * 消息构建器
     */
    public static class Builder {
        private final Message message;

        public Builder() {
            this.message = new Message();
        }

        public Builder type(MessageType type) {
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

        public Builder source(MessageSource source) {
            message.setSource(source);
            return this;
        }

        public Builder target(MessageTarget target) {
            message.setTarget(target);
            return this;
        }

        public Builder payload(JsonObject payload) {
            message.setPayload(payload);
            return this;
        }

        public Builder timestamp(long timestamp) {
            message.setTimestamp(timestamp);
            return this;
        }

        public Message build() {
            return message;
        }
    }
}
