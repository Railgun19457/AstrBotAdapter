package io.github.railgun19457.astrbotadapter.protocol;

public enum FrameType {
    HELLO("hello"),
    CHALLENGE("challenge"),
    AUTH("auth"),
    AUTH_RESULT("auth_result"),
    EVENT("event"),
    REQUEST("request"),
    RESPONSE("response"),
    ACK("ack"),
    HEARTBEAT("heartbeat"),
    ERROR("error");

    private final String wireName;

    FrameType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
