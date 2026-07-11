package io.github.railgun19457.astrbotadapter.transport;

import com.google.gson.JsonElement;
import io.github.railgun19457.astrbotadapter.protocol.Frame;

import java.util.concurrent.CompletionStage;

public interface FrameResponder {
    CompletionStage<Frame<?>> onFrame(Frame<JsonElement> frame);
}
