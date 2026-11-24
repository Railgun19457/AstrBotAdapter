package io.github.railgun19457.astrbotadapter.transport;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.railgun19457.astrbotadapter.protocol.SecureChannel;
import io.github.railgun19457.astrbotadapter.transport.AdapterServer.Handler;
import io.github.railgun19457.astrbotadapter.transport.AdapterServer.RequestFailure;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.protocols.Protocol;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static io.github.railgun19457.astrbotadapter.transport.AdapterServer.message;

public final class BackendLink implements AutoCloseable {
    private final URI url;
    private final String node;
    private final String upstream;
    private final String pairingId;
    private final byte[] pairingCode;
    private final TrustStore trust;
    private final Handler handler;
    private final Logger logger;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
        Thread.ofPlatform().daemon().name("astrbot-backend-link").factory());
    private volatile Connection connection;
    private volatile boolean closed;
    private volatile int retrySeconds = 1;

    public BackendLink(String node, Properties config, TrustStore trust, Handler handler, Logger logger) throws IOException {
        this.node = node;
        this.trust = trust;
        this.handler = handler;
        this.logger = logger;
        upstream = SecureChannel.nodeId(config.getProperty("backend.proxy_id"));
        url = URI.create(config.getProperty("backend.url", ""));
        String scheme = url.getScheme();
        String host = url.getHost();
        if (host == null || url.getUserInfo() != null || url.getFragment() != null || url.getQuery() != null
                || !("ws".equals(scheme) || "wss".equals(scheme))
                || (scheme.equals("ws") && !isLoopback(host))) {
            throw new IllegalArgumentException("backend.url must use wss:// or a loopback ws:// address");
        }
        pairingId = config.getProperty("backend.pairing_id", "");
        pairingCode = SecureChannel.bytes(config.getProperty("backend.pairing_code", ""));
        if (trust.keyFor(upstream) == null && (pairingId.length() != 22 || pairingCode.length != 24)) {
            throw new IllegalArgumentException("A backend pairing ID and code are required on first connection");
        }
    }

    private static boolean isLoopback(String host) throws IOException {
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (!address.isLoopbackAddress()) {
                return false;
            }
        }
        return true;
    }

    public String description() {
        Connection active = connection;
        String key = trust.keyFor(upstream);
        return "backend -> " + upstream + (ready() && key != null ? " (" + SecureChannel.fingerprint(key) + ")"
            : " (disconnected)");
    }

    public boolean ready() {
        Connection active = connection;
        return active != null && active.isOpen() && active.stage == Stage.READY;
    }

    public void start() {
        scheduler.execute(this::connect);
    }

    private synchronized void connect() {
        if (closed) {
            return;
        }
        var next = new Connection();
        connection = next;
        next.connect();
        scheduler.schedule(() -> {
            if (next.stage != Stage.READY && !next.isClosed()) {
                next.closeConnection(1008, "Handshake timed out");
            }
        }, 12, TimeUnit.SECONDS);
    }

    @Override
    public synchronized void close() {
        closed = true;
        scheduler.shutdownNow();
        if (connection != null) {
            connection.close();
        }
    }

    private synchronized void disconnected(Connection previous) {
        if (!closed && connection == previous) {
            scheduler.schedule(this::connect, retrySeconds, TimeUnit.SECONDS);
            retrySeconds = Math.min(30, retrySeconds * 2);
        }
    }

    private final class Connection extends WebSocketClient {
        private volatile Stage stage = Stage.CHALLENGE;
        private SecureChannel channel;
        private String key;
        private boolean enrolling;
        private final Set<String> pending = new HashSet<>();

        Connection() {
            super(url, new Draft_6455(List.of(), List.of(new Protocol("")), SecureChannel.MAX_FRAME), null, 5_000);
            setConnectionLostTimeout(30);
            if (url.getScheme().equals("wss")) {
                setSocketFactory(SSLSocketFactory.getDefault());
            }
        }

        @Override
        protected void onSetSSLParameters(SSLParameters parameters) {
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
        }

        @Override
        public void onOpen(ServerHandshake handshake) {}

        @Override
        public synchronized void onMessage(String payload) {
            try {
                var frame = JsonParser.parseString(payload).getAsJsonObject();
                switch (stage) {
                    case CHALLENGE -> prove(frame);
                    case RESULT -> {
                        expect(channel.decode(frame), "auth.result");
                        send(channel.encode(message("auth.confirm")));
                        stage = Stage.CONFIRM;
                    }
                    case CONFIRM -> {
                        expect(channel.decode(frame), "auth.ready");
                        if (enrolling) {
                            trust.pin(upstream, key);
                        }
                        retrySeconds = 1;
                        stage = Stage.READY;
                        logger.info("Authenticated backend connection to " + upstream);
                    }
                    case READY -> request(channel.decode(frame));
                }
            } catch (GeneralSecurityException | IOException | RuntimeException failure) {
                logger.warning("Rejected backend link: " + failure.getMessage());
                close(1008, "Rejected backend protocol message");
            }
        }

        private void prove(JsonObject frame) throws GeneralSecurityException {
            expect(frame, "auth.challenge");
            String server = SecureChannel.text(frame, "node", 64);
            String role = SecureChannel.text(frame, "role", 16);
            key = SecureChannel.text(frame, "key", 128);
            String ephemeralKey = SecureChannel.text(frame, "ephemeral", 128);
            String nonce = SecureChannel.text(frame, "nonce", 64);
            String trusted = trust.keyFor(upstream);
            enrolling = trusted == null;
            if (!server.equals(upstream) || !role.equals("velocity") || frame.get("version").getAsInt() != 1
                    || (!enrolling && !trusted.equals(key)) || SecureChannel.bytes(nonce).length != 32
                    || !SecureChannel.verify(SecureChannel.publicKey("Ed25519", key),
                        SecureChannel.transcript("challenge", server, role, key, ephemeralKey, nonce),
                        SecureChannel.text(frame, "signature", 128))) {
                throw new GeneralSecurityException("The Velocity identity or proof is invalid");
            }
            var ephemeral = SecureChannel.keyPair("X25519");
            String localKey = SecureChannel.base64(trust.identity().getPublic().getEncoded());
            String localEphemeral = SecureChannel.base64(ephemeral.getPublic().getEncoded());
            String localNonce = SecureChannel.randomToken(32);
            String pair = enrolling ? pairingId : "";
            byte[] code = enrolling ? pairingCode : new byte[0];
            byte[] proof = SecureChannel.transcript("authenticate", server, role, key, ephemeralKey, nonce,
                node, "backend", localKey, localEphemeral, localNonce, pair);
            channel = SecureChannel.derive(ephemeral.getPrivate(), ephemeralKey, proof, code, false);
            JsonObject response = message("auth.prove");
            response.addProperty("node", node);
            response.addProperty("key", localKey);
            response.addProperty("ephemeral", localEphemeral);
            response.addProperty("nonce", localNonce);
            response.addProperty("pairing_id", pair);
            response.addProperty("pairing_proof", enrolling ? SecureChannel.base64(SecureChannel.mac(code, proof)) : "");
            response.addProperty("signature", SecureChannel.sign(trust.identity().getPrivate(), proof));
            stage = Stage.RESULT;
            send(response.toString());
        }

        private void request(JsonObject frame) throws GeneralSecurityException {
            expect(frame, "request");
            String id = SecureChannel.text(frame, "id", 64);
            String operation = SecureChannel.text(frame, "op", 64);
            String caller = SecureChannel.nodeId(SecureChannel.text(frame, "caller", 64));
            String requestId = SecureChannel.text(frame, "request_id", 64);
            if (id.isBlank() || pending.contains(id)) {
                throw new GeneralSecurityException("Invalid backend request ID");
            }
            if (pending.size() >= 16) {
                respond(id, null, new RequestFailure("busy", "Too many outstanding requests"));
                return;
            }
            pending.add(id);
            CompletionStage<JsonObject> result;
            try {
                result = handler.request(upstream + "/" + caller, requestId, operation, frame.getAsJsonObject("data"));
            } catch (RuntimeException failure) {
                result = CompletableFuture.failedFuture(failure);
            }
            result.whenComplete((value, failure) -> respond(id, value, failure));
        }

        private synchronized void respond(String id, JsonObject value, Throwable error) {
            pending.remove(id);
            if (!isOpen()) {
                return;
            }
            JsonObject response = message("response");
            response.addProperty("id", id);
            response.addProperty("ok", error == null);
            if (error == null) {
                response.add("data", value);
            } else {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                response.addProperty("code", cause instanceof RequestFailure failure ? failure.code() : "internal_error");
                response.addProperty("message", cause instanceof RequestFailure ? cause.getMessage() : "The backend could not finish the request");
            }
            try {
                send(channel.encode(response));
            } catch (GeneralSecurityException | RuntimeException failure) {
                close(1011, "Could not send the response");
            }
        }

        @Override
        public void onMessage(ByteBuffer payload) {
            close(1003, "Text frames required");
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            disconnected(this);
        }

        @Override
        public void onError(Exception failure) {
            if (!closed) {
                logger.fine("Backend connection error: " + failure.getClass().getSimpleName());
            }
        }
    }

    private static void expect(JsonObject frame, String type) throws GeneralSecurityException {
        if (!type.equals(SecureChannel.text(frame, "type", 32))) {
            throw new GeneralSecurityException("Unexpected backend frame type");
        }
    }

    private enum Stage { CHALLENGE, RESULT, CONFIRM, READY }
}
