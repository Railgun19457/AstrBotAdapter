package io.github.railgun19457.astrbotadapter.transport;

import com.google.gson.JsonElement;
import io.github.railgun19457.astrbotadapter.protocol.Frame;

import java.util.concurrent.CompletionStage;

public interface FrameResponder {
    /**
     * Handle one inbound frame for a physical transport connection.
     *
     * @param connectionId Stable per-socket identifier used to bind auth/pairing state.
     * @param frame Validated inbound frame.
     * @return Optional response frame, or {@code null}/empty stage when no reply is needed.
     */
    CompletionStage<Frame<?>> onFrame(String connectionId, Frame<JsonElement> frame);

    /**
     * Release any connection-scoped authentication or pairing state.
     *
     * @param connectionId Stable per-socket identifier that was closed.
     */
    default void onConnectionClosed(String connectionId) {
    }
}
