package io.github.railgun19457.astrbotadapter.core.config;

import java.util.List;

public record CommandPolicyConfig(boolean enabled, List<String> allowlist, List<String> denylist, boolean allowAllServers) {
}
