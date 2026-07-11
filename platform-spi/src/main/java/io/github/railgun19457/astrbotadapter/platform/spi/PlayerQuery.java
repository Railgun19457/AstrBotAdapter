package io.github.railgun19457.astrbotadapter.platform.spi;

public record PlayerQuery(Type type, String value) {
    public enum Type {
        NAME,
        UUID
    }
}
