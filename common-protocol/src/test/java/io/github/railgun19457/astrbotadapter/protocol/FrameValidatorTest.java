package io.github.railgun19457.astrbotadapter.protocol;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FrameValidatorTest {
    @Test
    void acceptsValidRequest() {
        Frame<Map<String, Object>> frame = new Frame<>(
            1,
            "01TEST",
            FrameType.REQUEST,
            Ops.SERVER_STATUS,
            1710000000000L,
            1,
            new Route(new NodeRef(Role.ASTRBOT, "astrbot-main"), new NodeRef(Role.STANDALONE, "survival"), "01TRACE"),
            null,
            Target.server("survival"),
            null,
            null,
            Map.of()
        );

        assertTrue(new FrameValidator().validateEnvelope(frame).valid());
    }

    @Test
    void rejectsMissingTargetForRequest() {
        Frame<Map<String, Object>> frame = new Frame<>(
            1,
            "01TEST",
            FrameType.REQUEST,
            Ops.SERVER_STATUS,
            1710000000000L,
            1,
            new Route(new NodeRef(Role.ASTRBOT, "astrbot-main"), new NodeRef(Role.STANDALONE, "survival"), "01TRACE"),
            null,
            null,
            null,
            null,
            Map.of()
        );

        assertFalse(new FrameValidator().validateEnvelope(frame).valid());
    }

    @Test
    void rejectsInconsistentStatusAndTargetScope() {
        Frame<Map<String, Object>> frame = new Frame<>(
            1,
            "01TEST",
            FrameType.RESPONSE,
            Ops.SERVER_STATUS,
            1710000000000L,
            1,
            new Route(new NodeRef(Role.STANDALONE, "survival"), new NodeRef(Role.ASTRBOT, "astrbot-main"), "01TRACE"),
            null,
            Target.self(),
            "01REQUEST",
            new Status(false, StatusCode.OK, "invalid"),
            Map.of()
        );

        assertFalse(new FrameValidator().validateEnvelope(frame).valid());
    }
}
