package io.github.railgun19457.astrbotadapter.core.routing;

import com.google.gson.JsonElement;
import io.github.railgun19457.astrbotadapter.protocol.Frame;
import io.github.railgun19457.astrbotadapter.protocol.FrameType;
import io.github.railgun19457.astrbotadapter.protocol.Status;
import io.github.railgun19457.astrbotadapter.protocol.StatusCode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class RequestRouter {
    private final Map<String, OpHandler> handlers = new LinkedHashMap<>();

    public void register(OpHandler handler) {
        handlers.put(handler.op(), handler);
    }

    public CompletionStage<Frame<?>> route(RequestContext context, Frame<JsonElement> frame) {
        if (frame.type() != FrameType.REQUEST) {
            return CompletableFuture.completedFuture(context.frameFactory().errorTo(frame, StatusCode.BAD_REQUEST, "only request frames can be routed"));
        }
        OpHandler handler = handlers.get(frame.op());
        if (handler == null) {
            return CompletableFuture.completedFuture(context.frameFactory().responseTo(frame, Status.error(StatusCode.UNSUPPORTED_OPERATION, "unsupported op: " + frame.op()), Map.of()));
        }
        try {
            return handler.handle(context, frame);
        } catch (Throwable throwable) {
            context.platform().logger().error("handler failed for " + frame.op(), throwable);
            return CompletableFuture.completedFuture(context.frameFactory().responseTo(frame, Status.error(StatusCode.INTERNAL_ERROR, "internal error"), Map.of()));
        }
    }
}
