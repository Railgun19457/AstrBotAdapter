package io.github.railgun19457.astrbotadapter.core.routing;

import io.github.railgun19457.astrbotadapter.protocol.Frame;
import io.github.railgun19457.astrbotadapter.protocol.FrameType;
import io.github.railgun19457.astrbotadapter.protocol.NodeRef;
import io.github.railgun19457.astrbotadapter.protocol.Ops;
import io.github.railgun19457.astrbotadapter.protocol.ProtocolConstants;
import io.github.railgun19457.astrbotadapter.protocol.Route;
import io.github.railgun19457.astrbotadapter.protocol.Status;
import io.github.railgun19457.astrbotadapter.protocol.StatusCode;
import io.github.railgun19457.astrbotadapter.protocol.Target;
import io.github.railgun19457.astrbotadapter.util.Clock;
import io.github.railgun19457.astrbotadapter.util.Ids;

import java.util.concurrent.atomic.AtomicLong;

public final class FrameFactory {
    private final Clock clock;
    private final AtomicLong seq = new AtomicLong();

    public FrameFactory(Clock clock) {
        this.clock = clock;
    }

    public Frame<Object> responseTo(Frame<?> request, Status status, Object payload) {
        return new Frame<>(
            ProtocolConstants.VERSION,
            Ids.randomId(),
            FrameType.RESPONSE,
            request.op(),
            clock.nowMillis(),
            seq.incrementAndGet(),
            reverseRoute(request),
            null,
            null,
            request.id(),
            status,
            payload
        );
    }

    public Frame<Object> errorTo(Frame<?> request, StatusCode code, String message) {
        return new Frame<>(
            ProtocolConstants.VERSION,
            Ids.randomId(),
            FrameType.ERROR,
            Ops.CORE_ERROR,
            clock.nowMillis(),
            seq.incrementAndGet(),
            reverseRoute(request),
            null,
            null,
            request.id(),
            Status.error(code, message),
            new Object()
        );
    }

    public Frame<Object> event(NodeRef src, NodeRef dst, String traceId, String op, Target target, Object payload) {
        return new Frame<>(
            ProtocolConstants.VERSION,
            Ids.randomId(),
            FrameType.EVENT,
            op,
            clock.nowMillis(),
            seq.incrementAndGet(),
            new Route(src, dst, traceId),
            null,
            target,
            null,
            null,
            payload
        );
    }

    private Route reverseRoute(Frame<?> request) {
        return new Route(request.route().dst(), request.route().src(), request.route().traceId());
    }
}
