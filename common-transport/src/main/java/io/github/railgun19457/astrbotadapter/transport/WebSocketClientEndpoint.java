package io.github.railgun19457.astrbotadapter.transport;

import com.google.gson.JsonElement;
import io.github.railgun19457.astrbotadapter.protocol.Frame;
import io.github.railgun19457.astrbotadapter.protocol.FrameValidationResult;
import io.github.railgun19457.astrbotadapter.protocol.FrameType;
import io.github.railgun19457.astrbotadapter.protocol.FrameValidator;
import io.github.railgun19457.astrbotadapter.protocol.NodeRef;
import io.github.railgun19457.astrbotadapter.protocol.Ops;
import io.github.railgun19457.astrbotadapter.protocol.ProtocolConstants;
import io.github.railgun19457.astrbotadapter.protocol.ProtocolCodec;
import io.github.railgun19457.astrbotadapter.protocol.Route;
import io.github.railgun19457.astrbotadapter.protocol.payload.AuthPayloads;
import io.github.railgun19457.astrbotadapter.protocol.payload.CorePayloads;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class WebSocketClientEndpoint implements TransportEndpoint {
    private final URI uri;
    private final ProtocolCodec codec;
    private final FrameValidator validator;
    private final FrameResponder responder;
    private final TransportLogger logger;
    private final int maxFrameBytes;
    private final long heartbeatIntervalMillis;
    private final int heartbeatTimeoutsBeforeClose;
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "AstrBotAdapter-WebSocket-Reconnect");
        thread.setDaemon(true);
        return thread;
    });
    private Client client;
    private final AtomicLong outgoingSeq = new AtomicLong();
    private final DefaultRequestManager requestManager;
    private Runnable connectedListener = () -> {
    };
    private volatile boolean stopping;
    private int reconnectAttempts;

    public WebSocketClientEndpoint(URI uri, ProtocolCodec codec, FrameValidator validator, FrameResponder responder, TransportLogger logger) {
        this(uri, codec, validator, responder, logger, 1024 * 1024, 64, 30000L, 2);
    }

    public WebSocketClientEndpoint(URI uri, ProtocolCodec codec, FrameValidator validator, FrameResponder responder, TransportLogger logger, int maxFrameBytes, int maxPendingRequests, long heartbeatIntervalMillis, int heartbeatTimeoutsBeforeClose) {
        this.uri = uri;
        this.codec = codec;
        this.validator = validator;
        this.responder = responder;
        this.logger = logger;
        this.maxFrameBytes = Math.max(1024, maxFrameBytes);
        this.heartbeatIntervalMillis = Math.max(1000L, heartbeatIntervalMillis);
        this.heartbeatTimeoutsBeforeClose = Math.max(1, heartbeatTimeoutsBeforeClose);
        this.requestManager = new DefaultRequestManager(this::send, reconnectExecutor, Math.max(1, maxPendingRequests));
    }

    @Override
    public CompletionStage<Void> start() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        stopping = false;
        connectNewClient(future);
        return future;
    }

    public void onConnected(Runnable listener) {
        this.connectedListener = listener == null ? () -> {
        } : listener;
    }

    @Override
    public CompletionStage<Void> stop() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (client == null) {
            future.complete(null);
            return future;
        }
        stopping = true;
        reconnectExecutor.shutdownNow();
        requestManager.cancelAll("client endpoint stopped");
        client.close();
        future.complete(null);
        return future;
    }

    @Override
    public CompletionStage<Void> send(Frame<?> frame) {
        if (client != null && client.isOpen()) {
            client.send(codec.encode(withSeq(frame, outgoingSeq.incrementAndGet())));
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Frame<?>> sendRequest(Frame<?> frame, Duration timeout) {
        return requestManager.sendRequest(frame, timeout);
    }

    @Override
    public int connectionCount() {
        return client != null && client.isOpen() ? 1 : 0;
    }

    private Frame<Object> withSeq(Frame<?> frame, long seq) {
        return new Frame<>(
            frame.v(),
            frame.id(),
            frame.type(),
            frame.op(),
            frame.ts(),
            seq,
            frame.route(),
            frame.origin(),
            frame.target(),
            frame.replyTo(),
            frame.status(),
            frame.payload()
        );
    }

    private void connectNewClient(CompletableFuture<Void> connectFuture) {
        client = new Client(uri, connectFuture);
        client.connect();
    }

    private void scheduleReconnect() {
        if (stopping || reconnectExecutor.isShutdown()) {
            return;
        }
        long exponential = 1000L * (1L << Math.min(reconnectAttempts, 6));
        long delay = Math.min(60000L, exponential) + ThreadLocalRandom.current().nextLong(250L);
        reconnectAttempts++;
        logger.info("Scheduling WebSocket reconnect to " + uri + " in " + delay + "ms");
        reconnectExecutor.schedule(() -> connectNewClient(new CompletableFuture<>()), delay, TimeUnit.MILLISECONDS);
    }

    private final class Client extends WebSocketClient {
        private final CompletableFuture<Void> connectFuture;
        private SessionState state = SessionState.AUTHENTICATING;
        private String authenticatedRole;
        private String authenticatedId;
        private long lastIncomingSeq;
        private NodeRef localNode;
        private NodeRef remoteNode;
        private String sessionId;
        private String pendingPingId;
        private int heartbeatFailures;
        private ScheduledFuture<?> heartbeatTask;

        private Client(URI serverUri, CompletableFuture<Void> connectFuture) {
            super(serverUri);
            this.connectFuture = connectFuture;
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            logger.info("WebSocket client connected to " + uri);
            reconnectAttempts = 0;
            connectFuture.complete(null);
            connectedListener.run();
        }

        @Override
        public void onMessage(String message) {
            if (message.getBytes(StandardCharsets.UTF_8).length > maxFrameBytes) {
                close(1009, "payload too large");
                return;
            }
            Frame<JsonElement> frame;
            try {
                frame = codec.decode(message);
            } catch (RuntimeException exception) {
                close(1003, "invalid json frame");
                return;
            }
            FrameValidationResult validation = validator.validateEnvelope(frame);
            if (!validation.valid()) {
                close(1008, String.join(", ", validation.errors()));
                return;
            }
            if (frame.seq() <= lastIncomingSeq) {
                close(1008, "sequence regression");
                return;
            }
            lastIncomingSeq = frame.seq();
            if (state != SessionState.READY && !isAuthFrame(frame.type())) {
                close(1008, "authentication required");
                return;
            }
            if (state == SessionState.READY && !matchesAuthenticatedIdentity(frame)) {
                close(1008, "route source does not match authenticated session");
                return;
            }
            if (frame.type() == FrameType.RESPONSE || frame.type() == FrameType.ERROR) {
                requestManager.resolveResponse(frame);
            }
            if (frame.type() == FrameType.HEARTBEAT && Ops.CORE_PONG.equals(frame.op())) {
                handlePong(frame);
            }
            responder.onFrame("client", frame).whenComplete((response, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to handle frame " + frame.id(), throwable);
                    return;
                }
                if (response != null && isOpen()) {
                    WebSocketClientEndpoint.this.send(response);
                }
                if (frame.type() == FrameType.AUTH_RESULT && (frame.status() == null || frame.status().ok())) {
                    markReady(frame);
                }
            });
        }

        private void markReady(Frame<JsonElement> frame) {
            AuthPayloads.AuthResultPayload payload = codec.decodePayload(frame.payload(), AuthPayloads.AuthResultPayload.class);
            if (payload == null || !payload.ok()) {
                return;
            }
            state = SessionState.READY;
            authenticatedRole = frame.route().src().role().wireName();
            authenticatedId = frame.route().src().id();
            localNode = frame.route().dst();
            remoteNode = frame.route().src();
            sessionId = payload.sessionId();
            startHeartbeat();
        }

        private void startHeartbeat() {
            if (heartbeatTask != null && !heartbeatTask.isCancelled()) {
                return;
            }
            heartbeatTask = reconnectExecutor.scheduleAtFixedRate(this::sendHeartbeat, heartbeatIntervalMillis, heartbeatIntervalMillis, TimeUnit.MILLISECONDS);
        }

        private void sendHeartbeat() {
            if (state != SessionState.READY || sessionId == null || localNode == null || remoteNode == null || !isOpen()) {
                return;
            }
            if (pendingPingId != null) {
                heartbeatFailures++;
                if (heartbeatFailures >= heartbeatTimeoutsBeforeClose) {
                    close(1001, "heartbeat timeout");
                }
                return;
            }
            String pingId = java.util.UUID.randomUUID().toString();
            pendingPingId = pingId;
            WebSocketClientEndpoint.this.send(new Frame<>(
                ProtocolConstants.VERSION,
                pingId,
                FrameType.HEARTBEAT,
                Ops.CORE_PING,
                System.currentTimeMillis(),
                0L,
                new Route(localNode, remoteNode, java.util.UUID.randomUUID().toString()),
                null,
                null,
                null,
                null,
                new CorePayloads.HeartbeatPayload(sessionId, null)
            ));
        }

        private void handlePong(Frame<JsonElement> frame) {
            if (pendingPingId == null || !pendingPingId.equals(frame.replyTo())) {
                return;
            }
            CorePayloads.HeartbeatPayload payload = codec.decodePayload(frame.payload(), CorePayloads.HeartbeatPayload.class);
            if (payload == null || sessionId == null || !sessionId.equals(payload.sessionId())) {
                close(1008, "heartbeat session mismatch");
                return;
            }
            pendingPingId = null;
            heartbeatFailures = 0;
        }

        private boolean isAuthFrame(FrameType type) {
            return type == FrameType.HELLO
                || type == FrameType.CHALLENGE
                || type == FrameType.AUTH
                || type == FrameType.AUTH_RESULT
                || type == FrameType.ERROR;
        }

        private boolean matchesAuthenticatedIdentity(Frame<?> frame) {
            if (frame.route() == null || frame.route().src() == null || frame.route().src().role() == null) {
                return false;
            }
            return frame.route().src().role().wireName().equals(authenticatedRole) && frame.route().src().id().equals(authenticatedId);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            responder.onConnectionClosed("client");
            logger.info("WebSocket client closed: " + reason + " (" + code + ")");
            state = SessionState.AUTHENTICATING;
            authenticatedRole = null;
            authenticatedId = null;
            lastIncomingSeq = 0L;
            localNode = null;
            remoteNode = null;
            sessionId = null;
            pendingPingId = null;
            heartbeatFailures = 0;
            requestManager.cancelAll("client peer connection closed");
            if (heartbeatTask != null) {
                heartbeatTask.cancel(false);
                heartbeatTask = null;
            }
            scheduleReconnect();
        }

        @Override
        public void onError(Exception ex) {
            logger.error("WebSocket client error", ex);
            connectFuture.completeExceptionally(ex);
        }
    }
}
