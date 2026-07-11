package io.github.railgun19457.astrbotadapter.protocol;

public enum Role {
    ASTRBOT("astrbot"),
    STANDALONE("standalone"),
    VELOCITY("velocity"),
    BACKEND("backend");

    private final String wireName;

    Role(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
