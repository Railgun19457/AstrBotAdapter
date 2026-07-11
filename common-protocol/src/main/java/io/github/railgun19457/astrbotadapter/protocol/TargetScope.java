package io.github.railgun19457.astrbotadapter.protocol;

public enum TargetScope {
    SELF("self"),
    SERVER("server"),
    ALL_SERVERS("all_servers");

    private final String wireName;

    TargetScope(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
