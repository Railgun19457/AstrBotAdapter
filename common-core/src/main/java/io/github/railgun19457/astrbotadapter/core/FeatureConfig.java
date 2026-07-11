package io.github.railgun19457.astrbotadapter.core;

public record FeatureConfig(
    boolean chat,
    boolean player,
    boolean server,
    boolean command,
    boolean ai,
    boolean log,
    boolean cluster,
    boolean config
) {
    public static FeatureConfig defaults() {
        return new FeatureConfig(true, true, true, true, true, false, true, true);
    }
}
