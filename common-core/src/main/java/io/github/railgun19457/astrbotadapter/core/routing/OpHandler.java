package io.github.railgun19457.astrbotadapter.core.routing;

import com.google.gson.JsonElement;
import io.github.railgun19457.astrbotadapter.protocol.Frame;

import java.util.concurrent.CompletionStage;

public interface OpHandler {
    String op();

    CompletionStage<Frame<?>> handle(RequestContext context, Frame<JsonElement> request);
}
