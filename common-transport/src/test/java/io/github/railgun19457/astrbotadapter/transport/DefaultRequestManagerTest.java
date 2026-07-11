package io.github.railgun19457.astrbotadapter.transport;

import io.github.railgun19457.astrbotadapter.protocol.Frame;
import io.github.railgun19457.astrbotadapter.protocol.FrameType;
import io.github.railgun19457.astrbotadapter.protocol.NodeRef;
import io.github.railgun19457.astrbotadapter.protocol.Ops;
import io.github.railgun19457.astrbotadapter.protocol.ProtocolConstants;
import io.github.railgun19457.astrbotadapter.protocol.Role;
import io.github.railgun19457.astrbotadapter.protocol.Route;
import io.github.railgun19457.astrbotadapter.protocol.Status;
import io.github.railgun19457.astrbotadapter.protocol.Target;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DefaultRequestManagerTest {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @AfterEach
    void stopScheduler() {
        scheduler.shutdownNow();
    }

    @Test
    void resolvesResponseByReplyTo() throws Exception {
        DefaultRequestManager manager = new DefaultRequestManager(frame -> CompletableFuture.completedFuture(null), scheduler, 64);
        Frame<?> request = request("01REQ");
        CompletableFuture<Frame<?>> future = manager.sendRequest(request, Duration.ofSeconds(1)).toCompletableFuture();

        manager.resolveResponse(responseTo(request));

        assertEquals("01REQ", future.get(1, TimeUnit.SECONDS).replyTo());
        assertEquals(0, manager.pendingCount());
    }

    @Test
    void timesOutPendingRequest() {
        DefaultRequestManager manager = new DefaultRequestManager(frame -> CompletableFuture.completedFuture(null), scheduler, 64);
        CompletableFuture<Frame<?>> future = manager.sendRequest(request("01TIMEOUT"), Duration.ofMillis(20)).toCompletableFuture();

        assertThrows(Exception.class, () -> future.get(1, TimeUnit.SECONDS));
        assertEquals(0, manager.pendingCount());
    }

    @Test
    void rejectsWhenMaxPendingExceeded() {
        DefaultRequestManager manager = new DefaultRequestManager(frame -> CompletableFuture.completedFuture(null), scheduler, 1);
        manager.sendRequest(request("01ONE"), Duration.ofSeconds(1));

        CompletableFuture<Frame<?>> rejected = manager.sendRequest(request("01TWO"), Duration.ofSeconds(1)).toCompletableFuture();

        assertTrue(rejected.isCompletedExceptionally());
    }

    private Frame<Map<String, Object>> request(String id) {
        return new Frame<>(
            ProtocolConstants.VERSION,
            id,
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
    }

    private Frame<Map<String, Object>> responseTo(Frame<?> request) {
        return new Frame<>(
            ProtocolConstants.VERSION,
            "01RESP",
            FrameType.RESPONSE,
            request.op(),
            1710000000010L,
            2,
            new Route(request.route().dst(), request.route().src(), request.route().traceId()),
            null,
            null,
            request.id(),
            Status.success(),
            Map.of("ok", true)
        );
    }
}
