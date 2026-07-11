package io.github.railgun19457.astrbotadapter.protocol;

public enum ServerRole {
    STANDALONE("standalone"),
    VELOCITY("velocity"),
    BACKEND("backend");

    private final String wireName;

    ServerRole(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
