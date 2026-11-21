package io.github.railgun19457.astrbotadapter.protocol;

public enum StatusCode {
    OK("ok"),
    BAD_REQUEST("bad_request"),
    UNAUTHORIZED("unauthorized"),
    AUTH_FAILED("auth_failed"),
    AUTH_CODE_INVALID("auth_code_invalid"),
    AUTH_CODE_EXPIRED("auth_code_expired"),
    AUTH_CODE_USED("auth_code_used"),
    AUTH_CODE_ATTEMPTS_EXCEEDED("auth_code_attempts_exceeded"),
    PAIRING_FAILED("pairing_failed"),
    PROTOCOL_VERSION_UNSUPPORTED("protocol_version_unsupported"),
    UNSUPPORTED_OPERATION("unsupported_operation"),
    PERMISSION_DENIED("permission_denied"),
    TARGET_NOT_FOUND("target_not_found"),
    TARGET_OFFLINE("target_offline"),
    TIMEOUT("timeout"),
    RATE_LIMITED("rate_limited"),
    COMMAND_DISABLED("command_disabled"),
    COMMAND_BLOCKED("command_blocked"),
    PLAYER_NOT_FOUND("player_not_found"),
    PLAYER_OFFLINE("player_offline"),
    BACKEND_DISABLED("backend_disabled"),
    BACKEND_NOT_TRUSTED("backend_not_trusted"),
    PAYLOAD_TOO_LARGE("payload_too_large"),
    INTERNAL_ERROR("internal_error");

    private final String wireName;

    StatusCode(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
