package io.github.railgun19457.astrbotadapter.core.routing;

import io.github.railgun19457.astrbotadapter.platform.spi.PlatformAdapter;
import io.github.railgun19457.astrbotadapter.core.config.AdapterConfig;
import io.github.railgun19457.astrbotadapter.core.cluster.BackendRegistry;
import io.github.railgun19457.astrbotadapter.core.job.CommandJobStore;
import io.github.railgun19457.astrbotadapter.protocol.ProtocolCodec;

public record RequestContext(PlatformAdapter platform, ProtocolCodec codec, FrameFactory frameFactory, AdapterConfig config, BackendRegistry backendRegistry, CommandJobStore commandJobStore, CoreEventPublisher eventPublisher) {
}
