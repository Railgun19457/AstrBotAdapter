package io.github.railgun19457.astrbotadapter.core.routing;

import io.github.railgun19457.astrbotadapter.protocol.Target;

public interface CoreEventPublisher {
    void publish(String op, Target target, Object payload);
}
