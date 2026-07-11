package io.github.railgun19457.astrbotadapter.core;

import com.google.gson.JsonElement;
import io.github.railgun19457.astrbotadapter.core.auth.AuthTranscript;
import io.github.railgun19457.astrbotadapter.core.auth.CryptoUtil;
import io.github.railgun19457.astrbotadapter.core.auth.KeyStoreService;
import io.github.railgun19457.astrbotadapter.core.auth.LocalKeyPair;
import io.github.railgun19457.astrbotadapter.core.auth.PairingCodeService;
import io.github.railgun19457.astrbotadapter.core.auth.TrustedPublicKey;
import io.github.railgun19457.astrbotadapter.core.cluster.BackendRegistry;
import io.github.railgun19457.astrbotadapter.core.config.AdapterConfig;
import io.github.railgun19457.astrbotadapter.core.config.ConfigService;
import io.github.railgun19457.astrbotadapter.core.config.WebSocketConfig;
import io.github.railgun19457.astrbotadapter.core.job.CommandJobStore;
import io.github.railgun19457.astrbotadapter.core.routing.DefaultOpHandlers;
import io.github.railgun19457.astrbotadapter.core.routing.FrameFactory;
import io.github.railgun19457.astrbotadapter.core.routing.RequestContext;
import io.github.railgun19457.astrbotadapter.core.routing.RequestRouter;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformAdapter;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformChatEvent;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformPlayerEvent;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformRuntimeControl;
import io.github.railgun19457.astrbotadapter.protocol.Frame;
import io.github.railgun19457.astrbotadapter.protocol.FrameType;
import io.github.railgun19457.astrbotadapter.protocol.NodeRef;
import io.github.railgun19457.astrbotadapter.protocol.Ops;
import io.github.railgun19457.astrbotadapter.protocol.Origin;
import io.github.railgun19457.astrbotadapter.protocol.ProtocolCodec;
import io.github.railgun19457.astrbotadapter.protocol.ProtocolConstants;
import io.github.railgun19457.astrbotadapter.protocol.Route;
import io.github.railgun19457.astrbotadapter.protocol.Role;
import io.github.railgun19457.astrbotadapter.protocol.Status;
import io.github.railgun19457.astrbotadapter.protocol.StatusCode;
import io.github.railgun19457.astrbotadapter.protocol.Target;
import io.github.railgun19457.astrbotadapter.protocol.payload.AiPayloads;
import io.github.railgun19457.astrbotadapter.protocol.payload.AuthPayloads;
import io.github.railgun19457.astrbotadapter.protocol.payload.ChatPayloads;
import io.github.railgun19457.astrbotadapter.protocol.payload.CommonPayloads;
import io.github.railgun19457.astrbotadapter.protocol.payload.CorePayloads;
import io.github.railgun19457.astrbotadapter.protocol.payload.PlayerPayloads;
import io.github.railgun19457.astrbotadapter.protocol.payload.ServerPayloads;
import io.github.railgun19457.astrbotadapter.transport.TransportEndpoint;
import io.github.railgun19457.astrbotadapter.transport.TransportLogger;
import io.github.railgun19457.astrbotadapter.transport.WebSocketClientEndpoint;
import io.github.railgun19457.astrbotadapter.transport.WebSocketServerEndpoint;
import io.github.railgun19457.astrbotadapter.util.Ids;
import io.github.railgun19457.astrbotadapter.util.SystemClock;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class CoreRuntime implements PlatformRuntimeControl {
    private final PlatformAdapter platform;
    private final CapabilityService capabilityService;
    private final ProtocolCodec codec = new ProtocolCodec();
    private final FrameFactory frameFactory = new FrameFactory(SystemClock.INSTANCE);
    private final RequestRouter router = new RequestRouter();
    private final PairingCodeService pairingCodeService = new PairingCodeService();
    private final BackendRegistry backendRegistry = new BackendRegistry();
    private CommandJobStore commandJobStore;
    private final List<TransportEndpoint> astrbotEndpoints = new ArrayList<>();
    private final List<TransportEndpoint> backendEndpoints = new ArrayList<>();
    private final ConcurrentHashMap<String, Frame<JsonElement>> forwardedRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PendingAuth> pendingAuths = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ClientAuthState> pendingClientAuths = new ConcurrentHashMap<>();
    private KeyStoreService keyStoreService;
    private AdapterConfig config;
    private LocalKeyPair localKeyPair;
    private Set<String> capabilities = Set.of();
    private boolean running;

    public CoreRuntime(PlatformAdapter platform) {
        this(platform, new DefaultCapabilityService());
    }

    public CoreRuntime(PlatformAdapter platform, CapabilityService capabilityService) {
        this.platform = platform;
        this.capabilityService = capabilityService;
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        config = new ConfigService(platform).loadOrCreate();
        commandJobStore = new CommandJobStore(platform.configStore().dataDirectory().resolve("state").resolve("command-jobs.json"));
        keyStoreService = new KeyStoreService(platform);
        localKeyPair = keyStoreService.loadOrCreateLocalKey(config.auth().keyId());
        DefaultOpHandlers.registerAll(router);
        capabilities = capabilityService.capabilities(platform, config.features());
        platform.commandRegistry().registerCoreCommands(this);
        registerPlatformEvents();
        startTransports();
        platform.logger().info("AstrBotAdapter core started in " + platform.runtimeMode() + " mode with capabilities " + capabilities);
        running = true;
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        sendGoodbye("server_shutdown", "server is shutting down", 30000L);
        for (TransportEndpoint endpoint : allEndpoints()) {
            endpoint.stop();
        }
        astrbotEndpoints.clear();
        backendEndpoints.clear();
        platform.eventSource().unregisterAll();
        platform.commandRegistry().unregisterAll();
        running = false;
        platform.logger().info("AstrBotAdapter core stopped");
    }

    public boolean running() {
        return running;
    }

    @Override
    public String modeName() {
        return platform.runtimeMode().name();
    }

    @Override
    public String serverId() {
        return config == null ? platform.platformInfo().serverId() : config.serverId();
    }

    @Override
    public String localFingerprint() {
        return localKeyPair == null ? "unavailable" : localKeyPair.fingerprintSha256();
    }

    public Set<String> capabilities() {
        return capabilities;
    }

    public AdapterConfig config() {
        return config;
    }

    public LocalKeyPair localKeyPair() {
        return localKeyPair;
    }

    public PairingCodeService pairingCodeService() {
        return pairingCodeService;
    }

    public PairingCodeService.PairingCode createPairingCode(String targetId) {
        long ttl = config == null ? 300000L : config.auth().authCodeTtlMillis();
        return pairingCodeService.create(targetId, Duration.ofMillis(ttl));
    }

    @Override
    public String createAuthCode(String targetId) {
        PairingCodeService.PairingCode code = createPairingCode(targetId);
        return code.code() + " (expires_at=" + code.expiresAt() + ")";
    }

    @Override
    public String listAuthCodes() {
        if (pairingCodeService.list().isEmpty()) {
            return "No pending auth codes";
        }
        StringBuilder builder = new StringBuilder();
        pairingCodeService.list().values().forEach(code -> builder.append(code.code())
            .append(" target=").append(code.targetId())
            .append(" expires_at=").append(code.expiresAt())
            .append(" used=").append(code.used())
            .append(System.lineSeparator()));
        return builder.toString().trim();
    }

    @Override
    public boolean revokeAuthCode(String code) {
        return pairingCodeService.revoke(code);
    }

    @Override
    public synchronized String reloadConfig() {
        if (!running) {
            return "AstrBotAdapter is not running";
        }
        sendGoodbye("reload", "server is reloading AstrBotAdapter", 5000L);
        for (TransportEndpoint endpoint : allEndpoints()) {
            endpoint.stop();
        }
        astrbotEndpoints.clear();
        backendEndpoints.clear();
        platform.eventSource().unregisterAll();

        config = new ConfigService(platform).loadOrCreate();
        keyStoreService = new KeyStoreService(platform);
        localKeyPair = keyStoreService.loadOrCreateLocalKey(config.auth().keyId());
        capabilities = capabilityService.capabilities(platform, config.features());
        registerPlatformEvents();
        startTransports();
        return "Config reloaded. mode=" + platform.runtimeMode() + " server_id=" + serverId() + " capabilities=" + String.join(",", capabilities);
    }

    @Override
    public String connectionsStatus() {
        int astrbotConnections = astrbotEndpoints.stream().mapToInt(TransportEndpoint::connectionCount).sum();
        int backendConnections = backendEndpoints.stream().mapToInt(TransportEndpoint::connectionCount).sum();
        return "AstrBot endpoints=" + astrbotEndpoints.size()
            + " connections=" + astrbotConnections
            + ", backend endpoints=" + backendEndpoints.size()
            + " connections=" + backendConnections;
    }

    @Override
    public String listTrustedKeys() {
        if (keyStoreService == null || keyStoreService.allTrustedKeys().isEmpty()) {
            return "No trusted keys";
        }
        StringBuilder builder = new StringBuilder();
        keyStoreService.allTrustedKeys().forEach(key -> builder.append(key.domain())
            .append('/')
            .append(key.keyId())
            .append(" fingerprint=")
            .append(key.fingerprintSha256())
            .append(System.lineSeparator()));
        return builder.toString().trim();
    }

    @Override
    public boolean revokeTrustedKey(String domain, String keyId) {
        return keyStoreService != null && keyStoreService.removeTrustedKey(domain, keyId);
    }

    @Override
    public synchronized String regenerateLocalKey() {
        if (keyStoreService == null || config == null) {
            return "Key store is not ready";
        }
        localKeyPair = keyStoreService.regenerateLocalKey(config.auth().keyId());
        return "Local key regenerated. fingerprint=" + localKeyPair.fingerprintSha256();
    }

    public CompletionStage<Frame<?>> handleFrame(Frame<JsonElement> frame) {
        if (!isFrameAddressedToLocalNode(frame)) {
            return CompletableFuture.completedFuture(frameFactory.errorTo(frame, StatusCode.UNAUTHORIZED, "route destination does not match local node"));
        }
        if (frame.type() == FrameType.RESPONSE && forwardedRequests.containsKey(frame.replyTo())) {
            Frame<JsonElement> original = forwardedRequests.remove(frame.replyTo());
            Frame<Object> response = frameFactory.responseTo(original, frame.status() == null ? Status.success() : frame.status(), frame.payload());
            astrbotEndpoints.forEach(endpoint -> endpoint.send(response));
            return CompletableFuture.completedFuture(null);
        }
        if (shouldForwardToBackend(frame)) {
            Frame<Object> forwarded = forwardFrame(frame);
            forwardedRequests.put(forwarded.id(), frame);
            sendToBackend(frame.target().serverId(), forwarded);
            return CompletableFuture.completedFuture(null);
        }
        if (isVelocityTargetMissing(frame)) {
            return CompletableFuture.completedFuture(frameFactory.responseTo(frame, Status.error(StatusCode.TARGET_OFFLINE, "backend is offline or not registered"), java.util.Map.of()));
        }
        if (frame.type() == FrameType.REQUEST) {
            return router.route(new RequestContext(platform, codec, frameFactory, config, backendRegistry, commandJobStore, this::publishEvent), frame);
        }
        if (shouldForwardBackendEvent(frame)) {
            if (Ops.SERVER_SNAPSHOT.equals(frame.op())) {
                backendRegistry.updateSnapshot(codec.decodePayload(frame.payload(), ServerPayloads.ServerSnapshotPayload.class));
            }
            forwardBackendEvent(frame);
            return CompletableFuture.completedFuture(null);
        }
        if (frame.type() == FrameType.HEARTBEAT && Ops.CORE_PING.equals(frame.op())) {
            CorePayloads.HeartbeatPayload requestPayload = codec.decodePayload(frame.payload(), CorePayloads.HeartbeatPayload.class);
            return CompletableFuture.completedFuture(new Frame<>(
                ProtocolConstants.VERSION,
                Ids.randomId(),
                FrameType.HEARTBEAT,
                Ops.CORE_PONG,
                System.currentTimeMillis(),
                1,
                new Route(frame.route().dst(), frame.route().src(), frame.route().traceId()),
                null,
                null,
                frame.id(),
                null,
                new CorePayloads.HeartbeatPayload(requestPayload == null ? null : requestPayload.sessionId(), System.currentTimeMillis())
            ));
        }
        if (frame.type() == FrameType.HELLO) {
            return CompletableFuture.completedFuture(authChallenge(frame));
        }
        if (frame.type() == FrameType.CHALLENGE) {
            return CompletableFuture.completedFuture(authProve(frame));
        }
        if (frame.type() == FrameType.AUTH) {
            return CompletableFuture.completedFuture(authResult(frame));
        }
        if (frame.type() == FrameType.AUTH_RESULT) {
            if (!handleAuthResult(frame)) {
                return CompletableFuture.failedFuture(new IllegalStateException("authentication result verification failed"));
            }
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.completedFuture(null);
    }

    private void startTransports() {
        TransportLogger transportLogger = new PlatformTransportLogger();
        switch (platform.runtimeMode()) {
            case STANDALONE -> startServer(config.standaloneWebSocket(), transportLogger, EndpointRole.ASTRBOT);
            case VELOCITY -> {
                startServer(config.astrbotWebSocket(), transportLogger, EndpointRole.ASTRBOT);
                startServer(config.clusterWebSocket(), transportLogger, EndpointRole.BACKEND);
            }
            case VELOCITY_BACKEND -> startClient(config.velocityBackend().velocityUrl(), transportLogger, EndpointRole.BACKEND);
        }
    }

    private void registerPlatformEvents() {
        platform.eventSource().registerChatListener(this::onPlatformChat);
        platform.eventSource().registerJoinListener(this::onPlatformJoin);
        platform.eventSource().registerLeaveListener(this::onPlatformLeave);
    }

    private void onPlatformChat(PlatformChatEvent event) {
        String message = event.message();
        CommonPayloads.PlayerRef player = new CommonPayloads.PlayerRef(event.playerUuid().toString(), event.playerName(), event.playerName(), serverId());
        CommonPayloads.TextMessage text = CommonPayloads.TextMessage.plain(message, System.currentTimeMillis());
        if (config.ai().groupEnabled() && message.startsWith(config.ai().groupPrefix())) {
            publishEvent(Ops.AI_GROUP_REQUEST, new AiPayloads.AiGroupRequestPayload(
                player,
                CommonPayloads.TextMessage.plain(message.substring(config.ai().groupPrefix().length()), System.currentTimeMillis()),
                new AiPayloads.ConversationHint("server", serverId(), null)
            ));
        }
        if (config.ai().privateEnabled() && message.startsWith(config.ai().privatePrefix())) {
            publishEvent(Ops.AI_PRIVATE_REQUEST, new AiPayloads.AiPrivateRequestPayload(
                player,
                CommonPayloads.TextMessage.plain(message.substring(config.ai().privatePrefix().length()), System.currentTimeMillis()),
                new AiPayloads.ConversationHint("private", serverId(), event.playerUuid().toString())
            ));
        }
        if (config.chat().forwardEnabled()) {
            if (config.chat().forwardPrefix().isBlank() || message.startsWith(config.chat().forwardPrefix())) {
                String content = config.chat().forwardPrefix().isBlank() ? message : message.substring(config.chat().forwardPrefix().length());
                publishEvent(Ops.CHAT_PLAYER, new ChatPayloads.ChatPlayerPayload(player, CommonPayloads.TextMessage.plain(content, System.currentTimeMillis()), event.world(), "player_chat"));
            }
        }
    }

    private void onPlatformJoin(PlatformPlayerEvent event) {
        if (!config.chat().joinForwardEnabled()) {
            return;
        }
        CommonPayloads.PlayerRef player = new CommonPayloads.PlayerRef(event.playerUuid().toString(), event.playerName(), event.playerName(), serverId());
        publishEvent(Ops.PLAYER_JOIN, new PlayerPayloads.PlayerJoinPayload(player, event.onlineCount(), event.maxPlayers(), System.currentTimeMillis()));
    }

    private void onPlatformLeave(PlatformPlayerEvent event) {
        if (!config.chat().leaveForwardEnabled()) {
            return;
        }
        CommonPayloads.PlayerRef player = new CommonPayloads.PlayerRef(event.playerUuid().toString(), event.playerName(), event.playerName(), serverId());
        publishEvent(Ops.PLAYER_LEAVE, new PlayerPayloads.PlayerLeavePayload(player, event.onlineCount(), event.maxPlayers(), event.reason(), System.currentTimeMillis()));
    }

    private void publishEvent(String op, Object payload) {
        publishEvent(op, Target.server(serverId()), payload);
    }

    private void publishEvent(String op, Target target, Object payload) {
        NodeRef src = localNode();
        NodeRef dst = remoteEventNode();
        Frame<Object> frame = frameFactory.event(src, dst, Ids.randomId(), op, target, payload);
        List<TransportEndpoint> selected = platform.runtimeMode() == io.github.railgun19457.astrbotadapter.platform.spi.RuntimeMode.VELOCITY_BACKEND ? backendEndpoints : astrbotEndpoints;
        for (TransportEndpoint endpoint : selected) {
            endpoint.send(frame);
        }
    }

    private void sendGoodbye(String reason, String message, long retryAfterMs) {
        if (config == null) {
            return;
        }
        publishEvent(Ops.CORE_GOODBYE, Target.self(), new CorePayloads.GoodbyePayload(reason, message, retryAfterMs));
    }

    private void publishServerSnapshot() {
        if (platform.serverService().isEmpty()) {
            return;
        }
        io.github.railgun19457.astrbotadapter.platform.spi.ServerSnapshot snapshot = platform.serverService().get().snapshot();
        publishEvent(Ops.SERVER_SNAPSHOT, Target.server(snapshot.serverId()), new ServerPayloads.ServerSnapshotPayload(
            snapshot.serverId(),
            snapshot.role(),
            snapshot.onlineCount(),
            snapshot.maxPlayers(),
            null,
            null,
            null,
            snapshot.reportedAt()
        ));
    }

    private NodeRef localNode() {
        Role role = switch (platform.runtimeMode()) {
            case STANDALONE -> Role.STANDALONE;
            case VELOCITY -> Role.VELOCITY;
            case VELOCITY_BACKEND -> Role.BACKEND;
        };
        return new NodeRef(role, serverId());
    }

    private NodeRef remoteEventNode() {
        return switch (platform.runtimeMode()) {
            case STANDALONE, VELOCITY -> new NodeRef(Role.ASTRBOT, "*");
            case VELOCITY_BACKEND -> new NodeRef(Role.VELOCITY, "*");
        };
    }

    private void startServer(WebSocketConfig websocket, TransportLogger transportLogger, EndpointRole role) {
        if (!websocket.enabled()) {
            return;
        }
        WebSocketServerEndpoint endpoint = new WebSocketServerEndpoint(
            websocket.host(),
            websocket.port(),
            websocket.path(),
            codec,
            new io.github.railgun19457.astrbotadapter.protocol.FrameValidator(),
            this::handleFrame,
            transportLogger,
            config.rateLimit().maxFrameBytes(),
            config.rateLimit().maxPendingRequests()
        );
        addEndpoint(role, endpoint);
        endpoint.start().exceptionally(throwable -> {
            platform.logger().error("Failed to start WebSocket server", throwable);
            return null;
        });
    }

    private void startClient(String url, TransportLogger transportLogger, EndpointRole role) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            WebSocketClientEndpoint endpoint = new WebSocketClientEndpoint(
                URI.create(url),
                codec,
                new io.github.railgun19457.astrbotadapter.protocol.FrameValidator(),
                this::handleFrame,
                transportLogger,
                config.rateLimit().maxFrameBytes(),
                config.rateLimit().maxPendingRequests(),
                config.rateLimit().heartbeatIntervalMillis(),
                config.rateLimit().heartbeatTimeoutsBeforeClose()
            );
            addEndpoint(role, endpoint);
            endpoint.onConnected(() -> endpoint.send(authHello()));
            endpoint.start().exceptionally(throwable -> {
                platform.logger().error("Failed to connect WebSocket client", throwable);
                return null;
            });
        } catch (IllegalArgumentException exception) {
            platform.logger().error("Invalid backend websocket url", exception);
        }
    }

    private void addEndpoint(EndpointRole role, TransportEndpoint endpoint) {
        if (role == EndpointRole.ASTRBOT) {
            astrbotEndpoints.add(endpoint);
        } else {
            backendEndpoints.add(endpoint);
        }
    }

    private List<TransportEndpoint> allEndpoints() {
        ArrayList<TransportEndpoint> endpoints = new ArrayList<>();
        endpoints.addAll(astrbotEndpoints);
        endpoints.addAll(backendEndpoints);
        return endpoints;
    }

    private boolean isFrameAddressedToLocalNode(Frame<?> frame) {
        if (frame.route() == null || frame.route().dst() == null) {
            return false;
        }
        if (frame.type() == FrameType.HELLO && frame.route().dst().role() == localNode().role()) {
            return true;
        }
        String dst = frame.route().dst().id();
        return "*".equals(dst) || serverId().equals(dst);
    }

    private boolean shouldForwardToBackend(Frame<JsonElement> frame) {
        return platform.runtimeMode() == io.github.railgun19457.astrbotadapter.platform.spi.RuntimeMode.VELOCITY
            && frame.type() == FrameType.REQUEST
            && frame.target() != null
            && frame.target().serverId() != null
            && backendRegistry.has(frame.target().serverId())
            && hasReadyBackendSession(frame.target().serverId())
            && !(Ops.SERVER_STATUS.equals(frame.op()) && frame.target().scope() == io.github.railgun19457.astrbotadapter.protocol.TargetScope.ALL_SERVERS);
    }

    private boolean isVelocityTargetMissing(Frame<JsonElement> frame) {
        return platform.runtimeMode() == io.github.railgun19457.astrbotadapter.platform.spi.RuntimeMode.VELOCITY
            && frame.type() == FrameType.REQUEST
            && frame.target() != null
            && frame.target().serverId() != null
            && !serverId().equals(frame.target().serverId())
            && (!backendRegistry.has(frame.target().serverId()) || !hasReadyBackendSession(frame.target().serverId()));
    }

    private boolean hasReadyBackendSession(String backendId) {
        return backendEndpoints.stream().anyMatch(endpoint -> endpoint instanceof WebSocketServerEndpoint serverEndpoint
                && serverEndpoint.hasReadySession(Role.BACKEND, backendId));
    }

    private void sendToBackend(String backendId, Frame<?> frame) {
        backendEndpoints.stream()
            .filter(endpoint -> endpoint instanceof WebSocketServerEndpoint serverEndpoint
                && serverEndpoint.hasReadySession(Role.BACKEND, backendId))
            .findFirst()
            .ifPresent(endpoint -> endpoint.send(frame));
    }

    private boolean shouldForwardBackendEvent(Frame<JsonElement> frame) {
        return platform.runtimeMode() == io.github.railgun19457.astrbotadapter.platform.spi.RuntimeMode.VELOCITY
            && frame.type() == FrameType.EVENT
            && frame.route() != null
            && frame.route().src() != null
            && frame.route().src().role() == Role.BACKEND;
    }

    private void forwardBackendEvent(Frame<JsonElement> frame) {
        String originServerId = frame.target() == null || frame.target().serverId() == null ? frame.route().src().id() : frame.target().serverId();
        Frame<Object> forwarded = new Frame<>(
            ProtocolConstants.VERSION,
            Ids.randomId(),
            frame.type(),
            frame.op(),
            System.currentTimeMillis(),
            1,
            new Route(new NodeRef(Role.VELOCITY, serverId()), new NodeRef(Role.ASTRBOT, "*"), frame.route().traceId()),
            new Origin(Role.BACKEND, frame.route().src().id(), originServerId),
            frame.target(),
            null,
            null,
            frame.payload()
        );
        astrbotEndpoints.forEach(endpoint -> endpoint.send(forwarded));
    }

    private Frame<Object> forwardFrame(Frame<JsonElement> frame) {
        return new Frame<>(
            ProtocolConstants.VERSION,
            Ids.randomId(),
            frame.type(),
            frame.op(),
            System.currentTimeMillis(),
            1,
            new Route(new NodeRef(Role.VELOCITY, serverId()), new NodeRef(Role.BACKEND, frame.target().serverId()), frame.route().traceId()),
            null,
            Target.self(),
            null,
            null,
            frame.payload()
        );
    }

    private enum EndpointRole {
        ASTRBOT,
        BACKEND
    }

    private Frame<Object> authChallenge(Frame<JsonElement> hello) {
        AuthPayloads.AuthHelloPayload payload = codec.decodePayload(hello.payload(), AuthPayloads.AuthHelloPayload.class);
        String challengeId = Ids.randomId();
        String challengeFrameId = Ids.randomId();
        String serverNonce = Ids.randomId();
        long expiresAt = System.currentTimeMillis() + 30000L;
        if (payload == null || payload.protocol() == null || !ProtocolConstants.PROTOCOL_NAME.equals(payload.protocol().name())) {
            return frameFactory.errorTo(hello, StatusCode.PROTOCOL_VERSION_UNSUPPORTED, "invalid protocol hello");
        }
        AuthPayloads.ProtocolRange protocol = payload.protocol() == null
            ? new AuthPayloads.ProtocolRange(ProtocolConstants.PROTOCOL_NAME, ProtocolConstants.VERSION, ProtocolConstants.VERSION)
            : payload.protocol();
        if (protocol.minV() > protocol.maxV() || protocol.minV() > ProtocolConstants.VERSION || protocol.maxV() < ProtocolConstants.VERSION) {
            return frameFactory.errorTo(hello, StatusCode.PROTOCOL_VERSION_UNSUPPORTED, "no compatible protocol version");
        }
        pendingAuths.put(hello.route().traceId(), new PendingAuth(
            hello.route().src().role(),
            hello.route().src().id(),
            localNode().role(),
            serverId(),
            protocol.minV(),
            protocol.maxV(),
            ProtocolConstants.VERSION,
            payload.keyId(),
            payload.authCode(),
            payload.clientNonce(),
            List.copyOf(payload.capabilities() == null ? List.of() : payload.capabilities()),
            localKeyPair.keyId(),
            serverNonce,
            challengeId,
            expiresAt,
            hello.id(),
            challengeFrameId,
            hello.ts(),
            List.copyOf(capabilities)
        ));
        return new Frame<>(
            ProtocolConstants.VERSION,
            challengeFrameId,
            FrameType.CHALLENGE,
            Ops.AUTH_CHALLENGE,
            System.currentTimeMillis(),
            1,
            new Route(hello.route().dst(), hello.route().src(), hello.route().traceId()),
            null,
            null,
            null,
            null,
            new AuthPayloads.AuthChallengePayload(localKeyPair.keyId(), serverNonce, challengeId, expiresAt, ProtocolConstants.VERSION, List.copyOf(capabilities))
        );
    }

    private Frame<Object> authHello() {
        NodeRef src = localNode();
        NodeRef dst = remoteEventNode();
        String helloId = Ids.randomId();
        String traceId = Ids.randomId();
        String clientNonce = Ids.randomId();
        long now = System.currentTimeMillis();
        List<String> localCapabilities = List.copyOf(capabilities);
        pendingClientAuths.put(traceId, new ClientAuthState(
            src.role(),
            src.id(),
            dst.role(),
            dst.id(),
            ProtocolConstants.VERSION,
            ProtocolConstants.VERSION,
            localKeyPair.keyId(),
            clientNonce,
            localCapabilities,
            config.velocityBackend().authCode(),
            helloId,
            now,
            null,
            null,
            null,
            0L,
            null,
            0,
            List.of(),
            null,
            0L,
            ""
        ));
        return new Frame<>(
            ProtocolConstants.VERSION,
            helloId,
            FrameType.HELLO,
            Ops.AUTH_HELLO,
            now,
            1,
            new Route(src, dst, traceId),
            null,
            null,
            null,
            null,
            new AuthPayloads.AuthHelloPayload(new AuthPayloads.ProtocolRange(ProtocolConstants.PROTOCOL_NAME, ProtocolConstants.VERSION, ProtocolConstants.VERSION), localKeyPair.keyId(), clientNonce, config.velocityBackend().authCode(), localCapabilities)
        );
    }

    private Frame<Object> authProve(Frame<JsonElement> challenge) {
        AuthPayloads.AuthChallengePayload payload = codec.decodePayload(challenge.payload(), AuthPayloads.AuthChallengePayload.class);
        ClientAuthState state = pendingClientAuths.get(challenge.route().traceId());
        if (state == null) {
            state = new ClientAuthState(
                challenge.route().dst().role(),
                challenge.route().dst().id(),
                challenge.route().src().role(),
                challenge.route().src().id(),
                ProtocolConstants.VERSION,
                ProtocolConstants.VERSION,
                localKeyPair.keyId(),
                "",
                List.copyOf(capabilities),
                null,
                "",
                0L,
                null,
                null,
                null,
                0L,
                null,
                0,
                List.of(),
                null,
                0L,
                ""
            );
        }
        long authTs = System.currentTimeMillis();
        String authFrameId = Ids.randomId();
        String expectedServerFingerprint = trustedFingerprint(challenge.route().src().role(), payload.serverKeyId()).orElse("");
        AuthTranscript.ClientProof proof = new AuthTranscript.ClientProof(
            payload.selectedV(),
            state.clientMinV(),
            state.clientMaxV(),
            AuthTranscript.connectionRole(state.clientRole(), challenge.route().src().role()),
            state.clientRole(),
            state.clientId(),
            challenge.route().src().role(),
            challenge.route().src().id(),
            localKeyPair.keyId(),
            payload.serverKeyId(),
            localKeyPair.fingerprintSha256(),
            expectedServerFingerprint,
            state.clientNonce(),
            payload.serverNonce(),
            payload.challengeId(),
            payload.expiresAt(),
            state.helloId(),
            challenge.id(),
            state.helloTs(),
            authTs,
            AuthTranscript.capabilitiesSha256(state.clientCapabilities()),
            AuthTranscript.capabilitiesSha256(payload.capabilities())
        );
        String transcript = AuthTranscript.clientProof(proof);
        String signature;
        byte[] signatureBytes;
        try {
            signatureBytes = CryptoUtil.sign(localKeyPair.privateKey(), transcript);
            signature = CryptoUtil.base64Url(signatureBytes);
        } catch (GeneralSecurityException exception) {
            platform.logger().error("Failed to sign auth transcript", exception);
            signatureBytes = new byte[0];
            signature = "";
        }
        pendingClientAuths.put(challenge.route().traceId(), state.withChallenge(
            challenge.route().src().role(),
            challenge.route().src().id(),
            payload.serverKeyId(),
            payload.serverNonce(),
            payload.challengeId(),
            payload.expiresAt(),
            challenge.id(),
            payload.selectedV(),
            List.copyOf(payload.capabilities() == null ? List.of() : payload.capabilities()),
            authFrameId,
            authTs,
            CryptoUtil.signatureSha256(signatureBytes)
        ));
        return new Frame<>(
            ProtocolConstants.VERSION,
            authFrameId,
            FrameType.AUTH,
            Ops.AUTH_PROVE_CLIENT,
            authTs,
            2,
            new Route(challenge.route().dst(), challenge.route().src(), challenge.route().traceId()),
            null,
            null,
            null,
            null,
            new AuthPayloads.AuthPayload(localKeyPair.keyId(), localKeyPair.publicKeyOpenSsh(), signature)
        );
    }

    private Frame<Object> authResult(Frame<JsonElement> auth) {
        AuthPayloads.AuthPayload payload = codec.decodePayload(auth.payload(), AuthPayloads.AuthPayload.class);
        PendingAuth pending = pendingAuths.remove(auth.route().traceId());
        AuthDecision decision = authenticate(auth, payload, pending);
        Status status = decision.ok() ? Status.success() : Status.error(decision.code(), decision.message());
        String serverSignature = "";
        long authResultTs = System.currentTimeMillis();
        if (decision.ok() && pending != null) {
            try {
                String transcript = AuthTranscript.serverProof(new AuthTranscript.ServerProof(
                    pending.selectedV(),
                    pending.clientMinV(),
                    pending.clientMaxV(),
                    AuthTranscript.connectionRole(pending.clientRole(), pending.serverRole()),
                    pending.clientRole(),
                    pending.clientId(),
                    pending.serverRole(),
                    pending.serverId(),
                    pending.clientKeyId(),
                    pending.serverKeyId(),
                    decision.clientPublicKeySha256(),
                    localKeyPair.fingerprintSha256(),
                    pending.clientNonce(),
                    pending.serverNonce(),
                    pending.challengeId(),
                    pending.challengeExpiresAt(),
                    pending.helloId(),
                    pending.challengeFrameId(),
                    auth.id(),
                    authResultTs,
                    decision.clientSignatureSha256(),
                    AuthTranscript.capabilitiesSha256(pending.clientCapabilities()),
                    AuthTranscript.capabilitiesSha256(pending.serverCapabilities())
                ));
                serverSignature = CryptoUtil.base64Url(CryptoUtil.sign(localKeyPair.privateKey(), transcript));
            } catch (GeneralSecurityException exception) {
                platform.logger().error("Failed to sign auth result", exception);
            }
        }
        return new Frame<>(
            ProtocolConstants.VERSION,
            Ids.randomId(),
            FrameType.AUTH_RESULT,
            Ops.AUTH_RESULT,
            System.currentTimeMillis(),
            2,
            new Route(auth.route().dst(), auth.route().src(), auth.route().traceId()),
            null,
            null,
            null,
            status,
            new AuthPayloads.AuthResultPayload(decision.ok(), localKeyPair.keyId(), localKeyPair.publicKeyOpenSsh(), serverSignature, decision.ok() ? Ids.randomId() : null, authResultTs, List.copyOf(capabilities), status, "paired_key")
        );
    }

    private AuthDecision authenticate(Frame<?> auth, AuthPayloads.AuthPayload payload, PendingAuth pending) {
        if (!config.auth().enabled()) {
            return AuthDecision.success("", "");
        }
        if (pending == null) {
            return AuthDecision.fail(StatusCode.AUTH_FAILED, "missing auth challenge");
        }
        if (pending.challengeExpiresAt() < System.currentTimeMillis()) {
            return AuthDecision.fail(StatusCode.AUTH_CODE_EXPIRED, "auth challenge expired");
        }
        if (payload == null || payload.keyId() == null || payload.keyId().isBlank()) {
            return AuthDecision.fail(StatusCode.AUTH_FAILED, "missing key_id");
        }
        String domain = trustDomain(pending.clientRole());
        Optional<TrustedPublicKey> trusted = keyStoreService.trustedKeys(domain).stream()
            .filter(key -> key.keyId().equals(payload.keyId()))
            .findFirst();

        PublicKey publicKey = trusted.map(TrustedPublicKey::publicKey).orElse(null);
        if (payload.publicKey() != null && !payload.publicKey().isBlank()) {
            try {
                PublicKey provided = CryptoUtil.publicKeyFromText(payload.publicKey());
                if (publicKey != null && !CryptoUtil.fingerprintSha256(publicKey).equals(CryptoUtil.fingerprintSha256(provided))) {
                    return AuthDecision.fail(StatusCode.UNAUTHORIZED, "trusted key fingerprint mismatch");
                }
                publicKey = provided;
            } catch (GeneralSecurityException exception) {
                return AuthDecision.fail(StatusCode.AUTH_FAILED, "invalid public key");
            }
        }

        if (trusted.isEmpty()) {
            if (pending.authCode() == null || pending.authCode().isBlank()) {
                return AuthDecision.fail(StatusCode.AUTH_CODE_INVALID, "auth_code required for first pairing");
            }
            if (publicKey == null) {
                return AuthDecision.fail(StatusCode.AUTH_FAILED, "public key required for first pairing");
            }
        }

        if (payload.signature() == null || payload.signature().isBlank()) {
            return AuthDecision.fail(StatusCode.AUTH_FAILED, "missing signature");
        }
        try {
            byte[] signature = CryptoUtil.base64UrlDecode(payload.signature());
            String clientPublicKeySha256 = CryptoUtil.fingerprintSha256(publicKey);
            String expectedServerFingerprint = trusted.isEmpty() ? "" : localKeyPair.fingerprintSha256();
            String transcript = AuthTranscript.clientProof(new AuthTranscript.ClientProof(
                pending.selectedV(),
                pending.clientMinV(),
                pending.clientMaxV(),
                AuthTranscript.connectionRole(pending.clientRole(), pending.serverRole()),
                pending.clientRole(),
                pending.clientId(),
                pending.serverRole(),
                pending.serverId(),
                payload.keyId(),
                pending.serverKeyId(),
                clientPublicKeySha256,
                expectedServerFingerprint,
                pending.clientNonce(),
                pending.serverNonce(),
                pending.challengeId(),
                pending.challengeExpiresAt(),
                pending.helloId(),
                pending.challengeFrameId(),
                pending.clientHelloTs(),
                auth.ts(),
                AuthTranscript.capabilitiesSha256(pending.clientCapabilities()),
                AuthTranscript.capabilitiesSha256(pending.serverCapabilities())
            ));
            boolean verified = CryptoUtil.verify(publicKey, transcript, signature);
            if (!verified) {
                return AuthDecision.fail(StatusCode.AUTH_FAILED, "signature verification failed");
            }
            if (trusted.isEmpty()) {
                PairingCodeService.VerificationResult result = pairingCodeService.verifyAndUseDetailed(pending.authCode(), pending.clientId());
                if (result.status() != PairingCodeService.VerificationStatus.OK) {
                    return switch (result.status()) {
                        case EXPIRED -> AuthDecision.fail(StatusCode.AUTH_CODE_EXPIRED, "auth_code expired");
                        case USED -> AuthDecision.fail(StatusCode.AUTH_CODE_USED, "auth_code already used");
                        case MISSING, TARGET_MISMATCH -> AuthDecision.fail(StatusCode.AUTH_CODE_INVALID, "auth_code invalid");
                        case OK -> AuthDecision.success(clientPublicKeySha256, CryptoUtil.signatureSha256(signature));
                    };
                }
                keyStoreService.trust(domain, payload.keyId(), payload.publicKey());
                platform.logger().info("Trusted new " + pending.clientRole().wireName() + " key " + payload.keyId() + " fingerprint=" + clientPublicKeySha256);
            }
            return AuthDecision.success(clientPublicKeySha256, CryptoUtil.signatureSha256(signature));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            return AuthDecision.fail(StatusCode.AUTH_FAILED, "signature verification failed");
        }
    }

    private boolean handleAuthResult(Frame<JsonElement> authResult) {
        ClientAuthState state = pendingClientAuths.remove(authResult.route().traceId());
        AuthPayloads.AuthResultPayload payload = codec.decodePayload(authResult.payload(), AuthPayloads.AuthResultPayload.class);
        if (payload == null || !payload.ok()) {
            Status status = payload == null ? null : payload.status();
            platform.logger().warn("Authentication failed: " + (status == null ? "unknown" : status.message()));
            return false;
        }
        if (state == null) {
            platform.logger().warn("Authentication result has no pending challenge state");
            return false;
        }
        if (payload.keyId() == null || payload.keyId().isBlank() || payload.signature() == null || payload.signature().isBlank()) {
            platform.logger().warn("Authentication result is missing server key or signature");
            return false;
        }
        try {
            String domain = trustDomain(authResult.route().src().role());
            Optional<TrustedPublicKey> trusted = keyStoreService.trustedKeys(domain).stream()
                .filter(key -> key.keyId().equals(payload.keyId()))
                .findFirst();
            PublicKey publicKey = trusted.map(TrustedPublicKey::publicKey).orElse(null);
            if (payload.publicKey() != null && !payload.publicKey().isBlank()) {
                PublicKey provided = CryptoUtil.publicKeyFromText(payload.publicKey());
                if (publicKey != null && !CryptoUtil.fingerprintSha256(publicKey).equals(CryptoUtil.fingerprintSha256(provided))) {
                    platform.logger().warn("Authentication result server key fingerprint mismatch");
                    return false;
                }
                publicKey = provided;
            }
            if (publicKey == null) {
                platform.logger().warn("Authentication result has no trusted or provided server public key");
                return false;
            }
            String transcript = AuthTranscript.serverProof(new AuthTranscript.ServerProof(
                state.selectedV(),
                state.clientMinV(),
                state.clientMaxV(),
                AuthTranscript.connectionRole(state.clientRole(), state.serverRole()),
                state.clientRole(),
                state.clientId(),
                state.serverRole(),
                state.serverId(),
                state.clientKeyId(),
                payload.keyId(),
                localKeyPair.fingerprintSha256(),
                CryptoUtil.fingerprintSha256(publicKey),
                state.clientNonce(),
                state.serverNonce(),
                state.challengeId(),
                state.challengeExpiresAt(),
                state.helloId(),
                state.challengeFrameId(),
                state.authFrameId(),
                authResult.ts(),
                state.clientSignatureSha256(),
                AuthTranscript.capabilitiesSha256(state.clientCapabilities()),
                AuthTranscript.capabilitiesSha256(state.serverCapabilities())
            ));
            if (!CryptoUtil.verify(publicKey, transcript, CryptoUtil.base64UrlDecode(payload.signature()))) {
                platform.logger().warn("Authentication result server signature verification failed");
                return false;
            }
            if (trusted.isEmpty()) {
                platform.logger().warn("Authentication result used an untrusted server key; preconfigure the trusted public key before connecting");
                return false;
            }
            platform.logger().info("Authentication completed with " + authResult.route().src().role().wireName() + " " + authResult.route().src().id());
            if (platform.runtimeMode() == io.github.railgun19457.astrbotadapter.platform.spi.RuntimeMode.VELOCITY_BACKEND) {
                publishServerSnapshot();
            }
            return true;
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            platform.logger().warn("Authentication result verification failed: " + exception.getMessage());
            return false;
        }
    }

    private Optional<String> trustedFingerprint(Role role, String keyId) {
        if (keyId == null || keyId.isBlank() || keyStoreService == null) {
            return Optional.empty();
        }
        return keyStoreService.trustedKeys(trustDomain(role)).stream()
            .filter(key -> key.keyId().equals(keyId))
            .map(TrustedPublicKey::fingerprintSha256)
            .findFirst();
    }

    private String trustDomain(Role role) {
        return switch (role) {
            case ASTRBOT -> "astrbot";
            case VELOCITY -> "velocity";
            case BACKEND -> "backends";
            case STANDALONE -> "standalone";
        };
    }

    private record PendingAuth(
        Role clientRole,
        String clientId,
        Role serverRole,
        String serverId,
        int clientMinV,
        int clientMaxV,
        int selectedV,
        String clientKeyId,
        String authCode,
        String clientNonce,
        List<String> clientCapabilities,
        String serverKeyId,
        String serverNonce,
        String challengeId,
        long challengeExpiresAt,
        String helloId,
        String challengeFrameId,
        long clientHelloTs,
        List<String> serverCapabilities
    ) {
    }

    private record ClientAuthState(
        Role clientRole,
        String clientId,
        Role serverRole,
        String serverId,
        int clientMinV,
        int clientMaxV,
        String clientKeyId,
        String clientNonce,
        List<String> clientCapabilities,
        String authCode,
        String helloId,
        long helloTs,
        String serverKeyId,
        String serverNonce,
        String challengeId,
        long challengeExpiresAt,
        String challengeFrameId,
        int selectedV,
        List<String> serverCapabilities,
        String authFrameId,
        long authTs,
        String clientSignatureSha256
    ) {
        ClientAuthState withChallenge(Role serverRole, String serverId, String serverKeyId, String serverNonce, String challengeId, long challengeExpiresAt, String challengeFrameId, int selectedV, List<String> serverCapabilities, String authFrameId, long authTs, String clientSignatureSha256) {
            return new ClientAuthState(clientRole, clientId, serverRole, serverId, clientMinV, clientMaxV, clientKeyId, clientNonce, clientCapabilities, authCode, helloId, helloTs, serverKeyId, serverNonce, challengeId, challengeExpiresAt, challengeFrameId, selectedV, serverCapabilities, authFrameId, authTs, clientSignatureSha256);
        }
    }

    private record AuthDecision(boolean ok, StatusCode code, String message, String clientPublicKeySha256, String clientSignatureSha256) {
        static AuthDecision success(String clientPublicKeySha256, String clientSignatureSha256) {
            return new AuthDecision(true, StatusCode.OK, "success", clientPublicKeySha256, clientSignatureSha256);
        }

        static AuthDecision fail(StatusCode code, String message) {
            return new AuthDecision(false, code, message, "", "");
        }
    }

    private final class PlatformTransportLogger implements TransportLogger {
        @Override
        public void info(String message) {
            platform.logger().info(message);
        }

        @Override
        public void warn(String message) {
            platform.logger().warn(message);
        }

        @Override
        public void error(String message, Throwable throwable) {
            platform.logger().error(message, throwable);
        }
    }
}
