package io.github.railgun19457.astrbotadapter.core.i18n;

/**
 * 消息键枚举
 * 定义所有需要国际化的消息
 */
public enum MessageKey {

    // ===== 插件状态消息 =====
    PLUGIN_ENABLED("plugin.enabled"),
    PLUGIN_DISABLED("plugin.disabled"),
    PLUGIN_RELOADED("plugin.reloaded"),
    PLUGIN_RELOAD_FAILED("plugin.reload-failed"),

    // ===== 认证相关 =====
    AUTH_TOKEN_GENERATED("auth.token-generated"),
    AUTH_TOKEN_INVALID("auth.token-invalid"),
    AUTH_TOKEN_MISSING("auth.token-missing"),
    AUTH_SUCCESS("auth.success"),
    AUTH_FAILED("auth.failed"),

    // ===== WebSocket状态 =====
    WS_SERVER_STARTED("websocket.server-started"),
    WS_SERVER_STOPPED("websocket.server-stopped"),
    WS_SERVER_ERROR("websocket.server-error"),
    WS_CLIENT_CONNECTED("websocket.client-connected"),
    WS_CLIENT_DISCONNECTED("websocket.client-disconnected"),
    WS_STATUS_CONNECTED("websocket.status-connected"),
    WS_STATUS_DISCONNECTED("websocket.status-disconnected"),
    WS_STATUS_WAITING("websocket.status-waiting"),

    // ===== REST API状态 =====
    REST_SERVER_STARTED("rest.server-started"),
    REST_SERVER_STOPPED("rest.server-stopped"),
    REST_SERVER_ERROR("rest.server-error"),
    REST_RATE_LIMITED("rest.rate-limited"),

    // ===== 消息转发 =====
    FORWARD_ENABLED("forward.enabled"),
    FORWARD_DISABLED("forward.disabled"),
    FORWARD_SUCCESS("forward.success"),
    FORWARD_FAILED("forward.failed"),

    // ===== AI聊天 =====
    CHAT_GROUP_ENABLED("chat.group-enabled"),
    CHAT_GROUP_DISABLED("chat.group-disabled"),
    CHAT_PRIVATE_ENABLED("chat.private-enabled"),
    CHAT_PRIVATE_DISABLED("chat.private-disabled"),
    CHAT_REQUEST_SENT("chat.request-sent"),
    CHAT_RESPONSE_RECEIVED("chat.response-received"),
    CHAT_ERROR("chat.error"),

    // ===== 指令相关 =====
    COMMAND_NO_PERMISSION("command.no-permission"),
    COMMAND_PLAYER_ONLY("command.player-only"),
    COMMAND_CONSOLE_ONLY("command.console-only"),
    COMMAND_USAGE("command.usage"),
    COMMAND_UNKNOWN("command.unknown"),
    COMMAND_EXECUTED("command.executed"),
    COMMAND_EXECUTE_FAILED("command.execute-failed"),
    COMMAND_FILTERED("command.filtered"),

    // ===== 状态查询 =====
    STATUS_HEADER("status.header"),
    STATUS_WS_ENABLED("status.ws-enabled"),
    STATUS_WS_DISABLED("status.ws-disabled"),
    STATUS_WS_CLIENTS("status.ws-clients"),
    STATUS_REST_ENABLED("status.rest-enabled"),
    STATUS_REST_DISABLED("status.rest-disabled"),
    STATUS_UPTIME("status.uptime"),

    // ===== 玩家通知 =====
    NOTIFY_PLAYER_JOIN("notify.player-join"),
    NOTIFY_PLAYER_QUIT("notify.player-quit"),
    NOTIFY_SENT("notify.sent"),
    NOTIFY_FAILED("notify.failed"),

    // ===== 错误消息 =====
    ERROR_CONFIG_LOAD("error.config-load"),
    ERROR_CONFIG_SAVE("error.config-save"),
    ERROR_INTERNAL("error.internal"),
    ERROR_NETWORK("error.network"),
    ERROR_TIMEOUT("error.timeout"),
    ERROR_INVALID_REQUEST("error.invalid-request"),
    ERROR_PLAYER_NOT_FOUND("error.player-not-found"),
    ERROR_FEATURE_DISABLED("error.feature-disabled"),

    // ===== 重载相关 =====
    COMMAND_RELOAD_RELOADING("command.reload.reloading"),
    COMMAND_RELOAD_SUCCESS("command.reload.success"),
    COMMAND_RELOAD_FAILED("command.reload.failed"),

    // ===== 插件生命周期 =====
    PLUGIN_DISABLING("plugin.disabling"),

    // ===== 更新检查 =====
    UPDATE_CHECKING("update.checking"),
    UPDATE_AVAILABLE("update.available"),
    UPDATE_LATEST("update.latest"),
    UPDATE_CHECK_FAILED("update.check-failed");

    private final String key;

    MessageKey(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
