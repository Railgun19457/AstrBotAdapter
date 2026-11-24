package io.github.railgun19457.astrbotadapter.transport;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.railgun19457.astrbotadapter.protocol.SecureChannel;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.protocols.Protocol;
import org.java_websocket.server.WebSocketServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.Predicate;

public final class AdapterServer extends WebSocketServer {
    private static final Gson JSON = new Gson();
    private final String node;
    private final String path;
    private final String role;
    private final TrustStore trust;
    private final Handler handler;
    private final List<String> capabilities;
    private final Logger logger;
    private final Backends backends;
    private final Map<WebSocket, Peer> peers = new ConcurrentHashMap<>();
    private final Map<String, Peer> active = new ConcurrentHashMap<>();
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(
        Thread.ofPlatform().daemon().name("astrbot-handshake").factory()
    );

    public AdapterServer(InetSocketAddress address, String path, String node, String role, TrustStore trust,
                         List<String> capabilities, Handler handler, Logger logger) {
        this(address, path, node, role, trust, capabilities, handler, logger, null);
    }

    public AdapterServer(InetSocketAddress address, String path, String node, String role, TrustStore trust,
                         List<String> capabilities, Handler handler, Logger logger, Backends backends) {
        super(address, 2, List.of(new Draft_6455(List.of(), List.of(new Protocol("")), SecureChannel.MAX_FRAME)));
        this.node = node;
        this.path = path;
        this.role = role;
        this.trust = trust;
        this.handler = handler;
        this.capabilities = List.copyOf(capabilities);
        this.logger = logger;
        this.backends = backends;
        setConnectionLostTimeout(30);
    }

    @Override
    public void onOpen(WebSocket socket, ClientHandshake request) {
        boolean backend = backends != null && backends.path.equals(request.getResourceDescriptor());
        if ((!backend && !path.equals(request.getResourceDescriptor())) || peers.size() >= 128) {
            socket.close(1008, "Connection unavailable");
            return;
        }
        try {
            Peer peer = new Peer(socket, backend);
            peers.put(socket, peer);
            peer.challenge();
            deadlines.schedule(peer::expireHandshake, 12, TimeUnit.SECONDS);
        } catch (GeneralSecurityException | RuntimeException exception) {
            socket.close(1011, "Could not initialize the link");
        }
    }

    @Override
    public void onMessage(WebSocket socket, String payload) {
        Peer peer = peers.get(socket);
        if (peer == null) {
            return;
        }
        try {
            peer.receive(JsonParser.parseString(payload).getAsJsonObject());
        } catch (GeneralSecurityException | IOException | RuntimeException exception) {
            socket.close(1008, "Rejected protocol message");
        }
    }

    @Override
    public void onMessage(WebSocket socket, ByteBuffer payload) {
        socket.close(1003, "Text frames required");
    }

    @Override
    public void onClose(WebSocket socket, int code, String reason, boolean remote) {
        Peer peer = peers.remove(socket);
        if (peer != null && peer.client != null) {
            active.remove(peer.sessionId(), peer);
            peer.failPending();
        }
    }

    @Override
    public void onError(WebSocket socket, Exception exception) {
        if (socket == null) {
            logger.log(Level.WARNING, "AstrBot listener error", exception);
        }
    }

    @Override
    public void onStart() {
        logger.info("AstrBot WebSocket listening on " + getAddress() + path);
    }

    public void shutdown() {
        deadlines.shutdownNow();
        try {
            stop(1_000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    public void disconnect(String role, String client) {
        Peer peer = active.remove(role + ":" + client);
        if (peer != null) {
            peer.socket.close(1008, "Trust was revoked");
        }
    }

    public int connections() {
        return (int) active.values().stream().filter(peer -> !peer.backend && peer.ready()).count();
    }

    public List<String> connectionDetails() {
        return active.values().stream().filter(Peer::ready).map(peer -> peer.clientRole + " " + peer.client
            + " (" + SecureChannel.fingerprint(peer.clientKey) + ")").sorted().toList();
    }

    public void disconnect(String client) {
        disconnect("astrbot", client);
    }

    public boolean backendReady(String id) {
        Peer peer = active.get("backend:" + id);
        return peer != null && peer.ready();
    }

    public CompletionStage<JsonObject> requestBackend(String target, String client, String requestId,
                                                       String operation, JsonObject data) {
        Peer peer = active.get("backend:" + target);
        if (peer == null) {
            return CompletableFuture.failedFuture(new RequestFailure("backend_offline", "The backend adapter is disconnected"));
        }
        return peer.requestBackend(client, requestId, operation, data);
    }

    public void publish(String operation, JsonObject data) {
        JsonObject event = message("event");
        event.addProperty("op", operation);
        event.add("data", data);
        active.values().stream().filter(peer -> !peer.backend).forEach(peer -> peer.sendEvent(event));
    }

    public static JsonObject message(String type) {
        JsonObject message = new JsonObject();
        message.addProperty("type", type);
        return message;
    }

    private final class Peer {
        private final WebSocket socket;
        private final boolean backend;
        private final String clientRole;
        private final TrustStore peerTrust;
        private final String serverKey;
        private final KeyPair ephemeral = SecureChannel.keyPair("X25519");
        private final String serverEphemeral = SecureChannel.base64(ephemeral.getPublic().getEncoded());
        private final String serverNonce = SecureChannel.randomToken(32);
        private final Set<String> pending = new HashSet<>();
        private final Map<String, CompletableFuture<JsonObject>> outgoing = new ConcurrentHashMap<>();
        private volatile Stage stage = Stage.AUTH;
        private volatile String client;
        private String clientKey;
        private TrustStore.Pairing pairing;
        private SecureChannel channel;

        Peer(WebSocket socket, boolean backend) throws GeneralSecurityException {
            this.socket = socket;
            this.backend = backend;
            clientRole = backend ? "backend" : "astrbot";
            peerTrust = backend ? backends.trust : trust;
            serverKey = SecureChannel.base64(peerTrust.identity().getPublic().getEncoded());
        }

        String sessionId() {
            return clientRole + ":" + client;
        }

        boolean ready() {
            return stage == Stage.READY && socket.isOpen();
        }

        void challenge() throws GeneralSecurityException {
            JsonObject challenge = message("auth.challenge");
            challenge.addProperty("version", 1);
            challenge.addProperty("node", node);
            challenge.addProperty("role", role);
            challenge.addProperty("key", serverKey);
            challenge.addProperty("ephemeral", serverEphemeral);
            challenge.addProperty("nonce", serverNonce);
            challenge.addProperty("signature", SecureChannel.sign(peerTrust.identity().getPrivate(),
                SecureChannel.transcript("challenge", node, role, serverKey, serverEphemeral, serverNonce)));
            socket.send(JSON.toJson(challenge));
        }

        synchronized void expireHandshake() {
            if (stage != Stage.READY && socket.isOpen()) {
                socket.close(1008, "Handshake timed out");
            }
        }

        synchronized void receive(JsonObject frame) throws GeneralSecurityException, IOException {
            switch (stage) {
                case AUTH -> authenticate(frame);
                case CONFIRM -> confirm(channel.decode(frame));
                case READY -> {
                    JsonObject body = channel.decode(frame);
                    if (backend) {
                        backendResponse(body);
                    } else {
                        request(body);
                    }
                }
            }
        }

        private void authenticate(JsonObject frame) throws GeneralSecurityException {
            if (!"auth.prove".equals(SecureChannel.text(frame, "type", 32))) {
                throw new GeneralSecurityException("Expected client proof");
            }
            client = SecureChannel.nodeId(SecureChannel.text(frame, "node", 64));
            if (backend && !backends.registered.test(client)) {
                throw new GeneralSecurityException("Backend is not registered with Velocity");
            }
            clientKey = SecureChannel.text(frame, "key", 128);
            String clientEphemeral = SecureChannel.text(frame, "ephemeral", 128);
            String clientNonce = SecureChannel.text(frame, "nonce", 64);
            if (SecureChannel.bytes(clientNonce).length != 32) {
                throw new GeneralSecurityException("Invalid client nonce");
            }
            String pairingId = SecureChannel.text(frame, "pairing_id", 32);
            byte[] proof = SecureChannel.transcript("authenticate", node, role, serverKey, serverEphemeral,
                serverNonce, client, clientRole, clientKey, clientEphemeral, clientNonce, pairingId);
            byte[] code = new byte[0];
            if (pairingId.isEmpty()) {
                if (!peerTrust.trusted(client, clientKey)) {
                    throw new GeneralSecurityException("Unknown client identity");
                }
            } else {
                pairing = peerTrust.find(pairingId, client);
                if (pairing == null) {
                    throw new GeneralSecurityException("Invalid pairing ID");
                }
                code = SecureChannel.bytes(pairing.code());
                byte[] provided = SecureChannel.bytes(SecureChannel.text(frame, "pairing_proof", 64));
                if (!MessageDigest.isEqual(SecureChannel.mac(code, proof), provided)) {
                    throw new GeneralSecurityException("Invalid pairing proof");
                }
            }
            if (!SecureChannel.verify(SecureChannel.publicKey("Ed25519", clientKey), proof,
                SecureChannel.text(frame, "signature", 128))) {
                throw new GeneralSecurityException("Invalid client signature");
            }
            channel = SecureChannel.derive(ephemeral.getPrivate(), clientEphemeral, proof, code, true);
            stage = Stage.CONFIRM;
            socket.send(channel.encode(message("auth.result")));
        }

        private void confirm(JsonObject frame) throws GeneralSecurityException, IOException {
            if (!"auth.confirm".equals(SecureChannel.text(frame, "type", 32))) {
                throw new GeneralSecurityException("Expected key confirmation");
            }
            if (active.putIfAbsent(sessionId(), this) != null) {
                throw new GeneralSecurityException("This client already has a connection");
            }
            try {
                if (pairing != null) {
                    peerTrust.enroll(pairing, clientKey);
                    pairing = null;
                } else if (!peerTrust.trusted(client, clientKey)) {
                    throw new GeneralSecurityException("Client trust is no longer valid");
                }
                JsonObject ready = message("auth.ready");
                ready.add("capabilities", JSON.toJsonTree(capabilities));
                stage = Stage.READY;
                socket.send(channel.encode(ready));
            } catch (GeneralSecurityException | IOException | RuntimeException exception) {
                active.remove(sessionId(), this);
                throw exception;
            }
        }

        synchronized CompletionStage<JsonObject> requestBackend(String caller, String originalId, String operation, JsonObject data) {
            if (!ready() || !peerTrust.trusted(client, clientKey)) {
                return CompletableFuture.failedFuture(new RequestFailure("backend_offline", "The backend adapter is disconnected"));
            }
            if (outgoing.size() >= 16) {
                return CompletableFuture.failedFuture(new RequestFailure("busy", "Too many backend requests"));
            }
            String id = SecureChannel.randomToken(16);
            var result = new CompletableFuture<JsonObject>();
            outgoing.put(id, result);
            JsonObject request = message("request");
            request.addProperty("id", id);
            request.addProperty("caller", caller);
            request.addProperty("request_id", originalId);
            request.addProperty("op", operation);
            request.add("data", data);
            sendEvent(request);
            return result.orTimeout(5, TimeUnit.SECONDS).handle((response, error) -> {
                outgoing.remove(id, result);
                if (error instanceof java.util.concurrent.TimeoutException || error instanceof RequestFailure failure
                        && failure.code().equals("backend_offline")) {
                    throw new RequestFailure(operation.equals("command.run") ? "command_unknown" : "backend_offline",
                        operation.equals("command.run") ? "The command result is unknown" : "The backend did not reply");
                }
                if (error != null) {
                    throw new java.util.concurrent.CompletionException(error);
                }
                return response;
            });
        }

        void failPending() {
            outgoing.values().forEach(pending -> pending.completeExceptionally(
                new RequestFailure("backend_offline", "The backend adapter disconnected")));
        }

        private void backendResponse(JsonObject frame) throws GeneralSecurityException {
            if (!peerTrust.trusted(client, clientKey) || !"response".equals(SecureChannel.text(frame, "type", 16))) {
                throw new GeneralSecurityException("Expected a response from a trusted backend");
            }
            String id = SecureChannel.text(frame, "id", 64);
            var response = outgoing.get(id);
            if (response != null) {
                if (frame.get("ok").getAsBoolean()) {
                    response.complete(frame.getAsJsonObject("data"));
                } else {
                    response.completeExceptionally(new RequestFailure(SecureChannel.text(frame, "code", 64),
                        SecureChannel.text(frame, "message", 512)));
                }
            }
        }

        private void request(JsonObject frame) throws GeneralSecurityException {
            if (!trust.trusted(client, clientKey) || !"request".equals(SecureChannel.text(frame, "type", 16))) {
                throw new GeneralSecurityException("Expected a request from a trusted client");
            }
            String id = SecureChannel.text(frame, "id", 64);
            String operation = SecureChannel.text(frame, "op", 64);
            if (id.isBlank() || pending.contains(id)) {
                throw new GeneralSecurityException("Invalid or duplicate request ID");
            }
            JsonObject data = frame.getAsJsonObject("data");
            if (data == null) {
                throw new IllegalArgumentException("Missing request data");
            }
            if (pending.size() >= 16) {
                respond(id, null, "busy", "Too many outstanding requests");
                return;
            }
            pending.add(id);
            CompletionStage<JsonObject> response;
            try {
                response = handler.request(client, id, operation, data);
            } catch (RuntimeException error) {
                response = CompletableFuture.failedFuture(error);
            }
            response.whenCompleteAsync((value, error) -> {
                if (error == null) {
                    respond(id, value, null, null);
                } else {
                    Throwable cause = error.getCause() == null ? error : error.getCause();
                    if (cause instanceof RequestFailure failure) {
                        respond(id, null, failure.code(), failure.getMessage());
                    } else {
                        logger.log(Level.WARNING, "AstrBot request processing failed", cause);
                        respond(id, null, "internal_error", "The server could not finish the request");
                    }
                }
            });
        }

        private synchronized void respond(String id, JsonObject data, String code, String detail) {
            pending.remove(id);
            JsonObject response = message("response");
            response.addProperty("id", id);
            response.addProperty("ok", code == null);
            if (code == null) {
                response.add("data", data);
            } else {
                response.addProperty("code", code);
                response.addProperty("message", detail);
            }
            sendEvent(response);
        }

        private synchronized void sendEvent(JsonObject event) {
            if (stage != Stage.READY || !socket.isOpen()) {
                return;
            }
            try {
                socket.send(channel.encode(event));
            } catch (GeneralSecurityException | RuntimeException exception) {
                socket.close(1011, "Could not send the message");
            }
        }
    }

    private enum Stage { AUTH, CONFIRM, READY }

    public record Backends(String path, TrustStore trust, Predicate<String> registered) {}

    @FunctionalInterface
    public interface Handler {
        CompletionStage<JsonObject> request(String client, String id, String operation, JsonObject data);
    }

    public static final class RequestFailure extends RuntimeException {
        private final String code;

        public RequestFailure(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
