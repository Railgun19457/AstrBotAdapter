package io.github.railgun19457.astrbotadapter.protocol;

public record Target(TargetScope scope, String serverId) {
    public static Target self() {
        return new Target(TargetScope.SELF, null);
    }

    public static Target server(String serverId) {
        return new Target(TargetScope.SERVER, serverId);
    }

    public static Target allServers() {
        return new Target(TargetScope.ALL_SERVERS, null);
    }
}
