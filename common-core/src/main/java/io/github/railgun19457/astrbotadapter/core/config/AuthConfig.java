package io.github.railgun19457.astrbotadapter.core.config;

public record AuthConfig(String keyId, boolean enabled, String keysDirectory, long authCodeTtlMillis) {
}
