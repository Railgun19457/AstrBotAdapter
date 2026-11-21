package io.github.railgun19457.astrbotadapter.protocol;

public final class Ops {
    public static final String PAIR_INIT = "pair.init";
    public static final String PAIR_CHALLENGE = "pair.challenge";
    public static final String PAIR_PROVE = "pair.prove";
    public static final String PAIR_COMPLETE = "pair.complete";
    public static final String AUTH_HELLO = "auth.hello";
    public static final String AUTH_CHALLENGE = "auth.challenge";
    public static final String AUTH_PROVE_CLIENT = "auth.prove_client";
    public static final String AUTH_RESULT = "auth.result";
    public static final String CORE_ACK = "core.ack";
    public static final String CORE_PING = "core.ping";
    public static final String CORE_PONG = "core.pong";
    public static final String CORE_ERROR = "core.error";
    public static final String CORE_GOODBYE = "core.goodbye";
    public static final String CORE_CAPABILITIES_UPDATED = "core.capabilities.updated";
    public static final String CHAT_PLAYER = "chat.player";
    public static final String CHAT_EXTERNAL = "chat.external";
    public static final String CHAT_PRIVATE = "chat.private";
    public static final String PLAYER_JOIN = "player.join";
    public static final String PLAYER_LEAVE = "player.leave";
    public static final String PLAYER_LIST = "player.list";
    public static final String PLAYER_DETAIL = "player.detail";
    public static final String SERVER_STATUS = "server.status";
    public static final String SERVER_SNAPSHOT = "server.snapshot";
    public static final String COMMAND_EXECUTE = "command.execute";
    public static final String COMMAND_COMPLETED = "command.completed";
    public static final String AI_GROUP_REQUEST = "ai.group.request";
    public static final String AI_PRIVATE_REQUEST = "ai.private.request";
    public static final String AI_REPLY = "ai.reply";
    public static final String LOG_QUERY = "log.query";
    public static final String CONFIG_SYNC = "config.sync";

    private Ops() {
    }
}
