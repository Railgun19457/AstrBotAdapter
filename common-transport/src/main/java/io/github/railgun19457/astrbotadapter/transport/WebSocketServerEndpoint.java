package io.github.railgun19457.astrbotadapter.transport;

import com.google.gson.JsonElement;
import io.github.railgun19457.astrbotadapter.protocol.Frame;
import io.github.railgun19457.astrbotadapter.protocol.FrameValidationResult;
import io.github.railgun19457.astrbotadapter.protocol.FrameType;
import io.github.railgun19457.astrbotadapter.protocol.FrameValidator;
import io.github.railgun19457.astrbotadapter.protocol.ProtocolCodec;
import io.github.railgun19457.astrbotadapter.protocol.Role;
import io.github.railgun19457.astrbotadapter.protocol.Ops;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public final class WebSocketServerEndpoint implements TransportEndpoint {
    private static final Set<String> PRE_AUTH_OPS = Set.of(
        Ops.PAIR_INIT,
        Ops.PAIR_CHALLENGE,
        Ops.PAIR_PROVE,
        Ops.PAIR_COMPLETE,
        Ops.AUTH_HELLO,
        Ops.AUTH_CHALLENGE,
        Ops.AUTH_PROVE_CLIENT,
        Ops.AUTH_RESULT,
        Ops.CORE_PING,
        Ops.CORE_PONG,
        Ops.CORE_GOODBYE
    );

    private final String host;
    private final int port;
    private final String path;
    private final ProtocolCodec codec;
    private final FrameValidator validator;
    private final FrameResponder responder;
    private final TransportLogger logger;
    private final int maxFrameBytes;
    private final ScheduledExecutorService requestScheduler = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "AstrBotAdapter-WebSocket-Server-Requests");
        thread.setDaemon(true);
        return thread;
    });
    private final DefaultRequestManager requestManager;
    private Server server;

    public WebSocketServerEndpoint(String host, int port, String path, ProtocolCodec codec, FrameValidator validator, FrameResponder responder, TransportLogger logger) {
        this(host, port, path, codec, validator, responder, logger, 1024 * 1024, 64);
    }

    public WebSocketServerEndpoint(String host, int port, String path, ProtocolCodec codec, FrameValidator validator, FrameResponder responder, TransportLogger logger, int maxFrameBytes, int maxPendingRequests) {
        this.host = host;
        this.port = port;
        this.path = normalizePath(path);
        this.codec = codec;
        this.validator = validator;
        this.responder = responder;
        this.logger = logger;
        this.maxFrameBytes = Math.max(1024, maxFrameBytes);
        this.requestManager = new DefaultRequestManager(this::send, requestScheduler, Math.max(1, maxPendingRequests));
    }

    @Override
    public CompletionStage<Void> start() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            server = new Server(new InetSocketAddress(host, port));
            server.start();
            logger.info("WebSocket server started at ws://" + host + ":" + port + path);
            future.complete(null);
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        return future;
    }

    @Override
    public CompletionStage<Void> stop() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (server == null) {
            future.complete(null);
            return future;
        }
        try {
            server.stop(1000);
            requestManager.cancelAll("server endpoint stopped");
            requestScheduler.shutdownNow();
            logger.info("WebSocket server stopped");
            future.complete(null);
        } catch (Exception exception) {
            future.completeExceptionally(exception);
        }
        return future;
    }

    @Override
    public CompletionStage<Void> send(Frame<?> frame) {
        if (server != null) {
            server.sendFrame(frame);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Frame<?>> sendRequest(Frame<?> frame, Duration timeout) {
        return requestManager.sendRequest(frame, timeout);
    }

    public boolean hasReadySession(Role role, String id) {
        return server != null && server.hasReadySession(role, id);
    }

    @Override
    public int connectionCount() {
        return server == null ? 0 : server.sessions.size();
    }

    private String normalizePath(String value) {
        if (value == null || value.isBlank()) {
            return "/";
        }
        return value.startsWith("/") ? value : "/" + value;
    }

    private final class Server extends WebSocketServer {
        private final Map<WebSocket, SessionInfo> sessions = new ConcurrentHashMap<>();

        private Server(InetSocketAddress address) {
            super(address);
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            String requestedPath = normalizePath(handshake.getResourceDescriptor());
            if (!path.equals(requestedPath)) {
                conn.close(1008, "invalid path");
                return;
            }
            String connectionId = UUID.randomUUID().toString().replace("-", "");
            sessions.put(conn, new SessionInfo(connectionId, SessionState.AUTHENTICATING, null, null, 0L, 0L));
            logger.info("WebSocket connected from " + conn.getRemoteSocketAddress() + " connection=" + connectionId);
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            SessionInfo removed = sessions.remove(conn);
            if (removed != null) {
                responder.onConnectionClosed(removed.connectionId());
            }
            logger.info("WebSocket closed: " + reason + " (" + code + ")");
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            if (message.getBytes(StandardCharsets.UTF_8).length > maxFrameBytes) {
                conn.close(1009, "payload too large");
                return;
            }
            Frame<JsonElement> frame;
            try {
                frame = codec.decode(message);
            } catch (RuntimeException exception) {
                conn.close(1003, "invalid json frame");
                return;
            }
            FrameValidationResult validation = validator.validateEnvelope(frame);
            if (!validation.valid()) {
                conn.close(1008, String.join(", ", validation.errors()));
                return;
            }
            SessionInfo sessionInfo = sessions.get(conn);
            if (sessionInfo == null) {
                conn.close(1008, "unknown connection");
                return;
            }
            if (frame.seq() <= sessionInfo.lastSeq()) {
                conn.close(1008, "sequence regression");
                return;
            }
            sessions.put(conn, sessionInfo.withLastSeq(frame.seq()));
            if (sessionInfo.state() != SessionState.READY && !isAllowedPreAuthFrame(frame)) {
                conn.close(1008, "authentication required");
                return;
            }
            if (sessionInfo.state() == SessionState.READY && !matchesAuthenticatedIdentity(sessionInfo, frame)) {
                conn.close(1008, "route source does not match authenticated session");
                return;
            }
            if (frame.type() == FrameType.RESPONSE || frame.type() == FrameType.ERROR) {
                requestManager.resolveResponse(frame);
            }
            String connectionId = sessionInfo.connectionId();
            responder.onFrame(connectionId, frame).whenComplete((response, throwable) -> {
                if (throwable != null) {
                    logger.error("Failed to handle frame " + frame.id(), throwable);
                    return;
                }
                if (response != null && conn.isOpen()) {
                    Frame<?> sequenced = nextOutgoing(conn, response);
                    conn.send(codec.encode(sequenced));
                    if (frame.type() == FrameType.AUTH && response.type() == FrameType.AUTH_RESULT && response.status() != null && response.status().ok()
                        && (Ops.AUTH_PROVE_CLIENT.equals(frame.op()) || (Ops.PAIR_PROVE.equals(frame.op()) && Ops.PAIR_COMPLETE.equals(response.op())))) {
                        if (hasDuplicateReadySession(conn, frame.route().src().role().wireName(), frame.route().src().id())) {
                            conn.close(1008, "duplicate authenticated identity");
                            return;
                        }
                        sessions.computeIfPresent(conn, (ignored, current) -> current.authenticated(frame.route().src().role().wireName(), frame.route().src().id()));
                    }
                }
            });
        }

        private boolean hasDuplicateReadySession(WebSocket current, String role, String id) {
            return sessions.entrySet().stream().anyMatch(entry -> entry.getKey() != current
                && entry.getKey().isOpen()
                && entry.getValue().state() == SessionState.READY
                && role.equals(entry.getValue().role())
                && id.equals(entry.getValue().id()));
        }

        private void sendFrame(Frame<?> frame) {
            if (frame.route() == null || frame.route().dst() == null || "*".equals(frame.route().dst().id())) {
                sessions.forEach((socket, session) -> {
                    if (socket.isOpen() && session.state() == SessionState.READY) {
                        socket.send(codec.encode(nextOutgoing(socket, frame)));
                    }
                });
                return;
            }
            String dstRole = frame.route().dst().role().wireName();
            String dstId = frame.route().dst().id();
            sessions.forEach((socket, session) -> {
                if (socket.isOpen() && session.state() == SessionState.READY && dstRole.equals(session.role()) && dstId.equals(session.id())) {
                    socket.send(codec.encode(nextOutgoing(socket, frame)));
                }
            });
        }

        private boolean hasReadySession(Role role, String id) {
            return sessions.values().stream().anyMatch(session -> session.state() == SessionState.READY
                && role.wireName().equals(session.role())
                && id.equals(session.id()));
        }

        private Frame<?> nextOutgoing(WebSocket conn, Frame<?> frame) {
            SessionInfo session = sessions.get(conn);
            if (session == null) {
                return withSeq(frame, 1L);
            }
            long seq = session.lastOutgoingSeq() + 1;
            sessions.put(conn, session.withLastOutgoingSeq(seq));
            return withSeq(frame, seq);
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

        private boolean isAllowedPreAuthFrame(Frame<?> frame) {
            if (frame.type() == FrameType.ERROR) {
                return true;
            }
            if (frame.op() == null || frame.op().isBlank()) {
                return false;
            }
            return PRE_AUTH_OPS.contains(frame.op());
        }

        private boolean matchesAuthenticatedIdentity(SessionInfo sessionInfo, Frame<?> frame) {
            if (frame.route() == null || frame.route().src() == null || frame.route().src().role() == null) {
                return false;
            }
            return frame.route().src().role().wireName().equals(sessionInfo.role()) && frame.route().src().id().equals(sessionInfo.id());
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            logger.error("WebSocket error", ex);
        }

        @Override
        public void onStart() {
            setConnectionLostTimeout(60);
        }

        private record SessionInfo(String connectionId, SessionState state, String role, String id, long lastSeq, long lastOutgoingSeq) {
            private SessionInfo withLastSeq(long seq) {
                return new SessionInfo(connectionId, state, role, id, seq, lastOutgoingSeq);
            }

            private SessionInfo withLastOutgoingSeq(long seq) {
                return new SessionInfo(connectionId, state, role, id, lastSeq, seq);
            }

            private SessionInfo authenticated(String role, String id) {
                return new SessionInfo(connectionId, SessionState.READY, role, id, lastSeq, lastOutgoingSeq);
            }
        }
    }
}
