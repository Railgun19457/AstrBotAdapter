package io.github.railgun19457.astrbotadapter.protocol;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProtocolCodecTest {
    @Test
    void roundTripsEnvelopeWithWireNames() {
        ProtocolCodec codec = new ProtocolCodec();
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
            Map.of("include_performance", true)
        );

        String json = codec.encode(frame);
        assertTrue(json.contains("\"type\":\"request\""));
        assertTrue(json.contains("\"trace_id\""));
        assertTrue(json.contains("\"server_id\""));

        Frame<?> decoded = codec.decode(json);
        assertEquals(FrameType.REQUEST, decoded.type());
        assertEquals(Role.ASTRBOT, decoded.route().src().role());
        assertEquals(TargetScope.SERVER, decoded.target().scope());
    }
}
