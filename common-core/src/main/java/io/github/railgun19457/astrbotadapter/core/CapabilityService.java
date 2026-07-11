package io.github.railgun19457.astrbotadapter.core;

import io.github.railgun19457.astrbotadapter.platform.spi.PlatformAdapter;

import java.util.Set;

public interface CapabilityService {
    Set<String> capabilities(PlatformAdapter platform, FeatureConfig config);
}
