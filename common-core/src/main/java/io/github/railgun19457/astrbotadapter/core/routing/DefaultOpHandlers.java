package io.github.railgun19457.astrbotadapter.core.routing;

import com.google.gson.JsonElement;
import io.github.railgun19457.astrbotadapter.core.job.CommandJobStore;
import io.github.railgun19457.astrbotadapter.platform.spi.PlayerDetail;
import io.github.railgun19457.astrbotadapter.platform.spi.PlayerSelector;
import io.github.railgun19457.astrbotadapter.platform.spi.PlayerSummary;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformAdapter;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformCommandService;
import io.github.railgun19457.astrbotadapter.platform.spi.ServerStatusItem;
import io.github.railgun19457.astrbotadapter.platform.spi.TextOutboundMessage;
import io.github.railgun19457.astrbotadapter.protocol.Frame;
import io.github.railgun19457.astrbotadapter.protocol.Ops;
import io.github.railgun19457.astrbotadapter.protocol.ServerRole;
import io.github.railgun19457.astrbotadapter.protocol.Status;
import io.github.railgun19457.astrbotadapter.protocol.StatusCode;
import io.github.railgun19457.astrbotadapter.protocol.Target;
import io.github.railgun19457.astrbotadapter.protocol.payload.AiPayloads;
import io.github.railgun19457.astrbotadapter.protocol.payload.ChatPayloads;
import io.github.railgun19457.astrbotadapter.protocol.payload.CommandPayloads;
import io.github.railgun19457.astrbotadapter.protocol.payload.CommonPayloads;
import io.github.railgun19457.astrbotadapter.protocol.payload.ConfigPayloads;
import io.github.railgun19457.astrbotadapter.protocol.payload.LogPayloads;
import io.github.railgun19457.astrbotadapter.protocol.payload.PlayerPayloads;
import io.github.railgun19457.astrbotadapter.protocol.payload.ServerPayloads;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import io.github.railgun19457.astrbotadapter.util.Ids;

public final class DefaultOpHandlers {
    private static final ConcurrentHashMap<String, String> APPLIED_CONFIG_REVISIONS = new ConcurrentHashMap<>();
    private DefaultOpHandlers() {
    }

    public static void registerAll(RequestRouter router) {
        router.register(new ServerStatusHandler());
        router.register(new PlayerListHandler());
        router.register(new PlayerDetailHandler());
        router.register(new ChatExternalHandler());
        router.register(new ChatPrivateHandler());
        router.register(new AiReplyHandler());
        router.register(new CommandExecuteHandler());
        router.register(new LogQueryHandler());
        router.register(new ConfigSyncHandler());
    }

    private abstract static class BaseHandler implements OpHandler {
        <T> T payload(RequestContext context, Frame<JsonElement> frame, Class<T> type) {
            return context.codec().decodePayload(frame.payload(), type);
        }

        Frame<?> unsupported(RequestContext context, Frame<?> request, String message) {
            return context.frameFactory().responseTo(request, Status.error(StatusCode.UNSUPPORTED_OPERATION, message), Map.of());
        }

        Frame<?> ok(RequestContext context, Frame<?> request, Object payload) {
            return context.frameFactory().responseTo(request, Status.success(), payload);
        }
    }

    private static final class ServerStatusHandler extends BaseHandler {
        @Override
        public String op() {
            return Ops.SERVER_STATUS;
        }

        @Override
        public CompletionStage<Frame<?>> handle(RequestContext context, Frame<JsonElement> request) {
            PlatformAdapter platform = context.platform();
            if (platform.serverService().isEmpty()) {
                return CompletableFuture.completedFuture(unsupported(context, request, "server service unavailable"));
            }
            ServerPayloads.ServerStatusRequest payload = payload(context, request, ServerPayloads.ServerStatusRequest.class);
            return platform.serverService().get().status(payload.includePerformance()).thenApply(item -> ok(context, request, new ServerPayloads.ServerStatusResponse(
                mergeServerItems(toPayload(item), context.backendRegistry().statusItems()),
                List.of(),
                System.currentTimeMillis()
            )));
        }
    }

    private static final class PlayerListHandler extends BaseHandler {
        @Override
        public String op() {
            return Ops.PLAYER_LIST;
        }

        @Override
        public CompletionStage<Frame<?>> handle(RequestContext context, Frame<JsonElement> request) {
            if (context.platform().playerService().isEmpty()) {
                return CompletableFuture.completedFuture(unsupported(context, request, "player service unavailable"));
            }
            PlayerPayloads.PlayerListRequest payload = payload(context, request, PlayerPayloads.PlayerListRequest.class);
            return context.platform().playerService().get().onlinePlayers(payload.includeDetails()).thenApply(players -> ok(context, request, new PlayerPayloads.PlayerListResponse(
                List.of(new PlayerPayloads.PlayerServerItem(
                    context.platform().platformInfo().serverId(),
                    context.platform().platformInfo().displayName(),
                    context.platform().platformInfo().role(),
                    Status.success(),
                    players.size(),
                    players.stream().map(DefaultOpHandlers::toPayload).toList()
                )),
                List.of(),
                System.currentTimeMillis()
            )));
        }
    }

    private static final class PlayerDetailHandler extends BaseHandler {
        @Override
        public String op() {
            return Ops.PLAYER_DETAIL;
        }

        @Override
        public CompletionStage<Frame<?>> handle(RequestContext context, Frame<JsonElement> request) {
            if (context.platform().playerService().isEmpty()) {
                return CompletableFuture.completedFuture(unsupported(context, request, "player service unavailable"));
            }
            PlayerPayloads.PlayerDetailRequest payload = payload(context, request, PlayerPayloads.PlayerDetailRequest.class);
            io.github.railgun19457.astrbotadapter.platform.spi.PlayerQuery.Type type = "uuid".equalsIgnoreCase(payload.query().type())
                ? io.github.railgun19457.astrbotadapter.platform.spi.PlayerQuery.Type.UUID
                : io.github.railgun19457.astrbotadapter.platform.spi.PlayerQuery.Type.NAME;
            return context.platform().playerService().get().findPlayer(new io.github.railgun19457.astrbotadapter.platform.spi.PlayerQuery(type, payload.query().value()), payload.includeHistory())
                .thenApply(player -> player.<Frame<?>>map(value -> ok(context, request, new PlayerPayloads.PlayerDetailResponse(toPayload(value))))
                    .orElseGet(() -> context.frameFactory().responseTo(request, Status.error(StatusCode.PLAYER_NOT_FOUND, "player not found"), Map.of())));
        }
    }

    private static final class ChatExternalHandler extends BaseHandler {
        @Override
        public String op() {
            return Ops.CHAT_EXTERNAL;
        }

        @Override
        public CompletionStage<Frame<?>> handle(RequestContext context, Frame<JsonElement> request) {
            if (context.platform().chatService().isEmpty()) {
                return CompletableFuture.completedFuture(unsupported(context, request, "chat service unavailable"));
            }
            ChatPayloads.ChatExternalPayload payload = payload(context, request, ChatPayloads.ChatExternalPayload.class);
            return context.platform().chatService().get().broadcast(formatExternalMessage(context, payload)).thenApply(ignored -> ok(context, request, Map.of("delivered", true)));
        }
    }

    private static final class ChatPrivateHandler extends BaseHandler {
        @Override
        public String op() {
            return Ops.CHAT_PRIVATE;
        }

        @Override
        public CompletionStage<Frame<?>> handle(RequestContext context, Frame<JsonElement> request) {
            if (context.platform().chatService().isEmpty()) {
                return CompletableFuture.completedFuture(unsupported(context, request, "chat service unavailable"));
            }
            ChatPayloads.ChatPrivatePayload payload = payload(context, request, ChatPayloads.ChatPrivatePayload.class);
            PlayerSelector selector = new PlayerSelector(parseUuid(payload.player().uuid()), payload.player().name());
            return context.platform().chatService().get().sendPrivate(selector, toText(payload.message())).thenApply(ignored -> ok(context, request, Map.of("delivered", true)));
        }
    }

    private static final class AiReplyHandler extends BaseHandler {
        @Override
        public String op() {
            return Ops.AI_REPLY;
        }

        @Override
        public CompletionStage<Frame<?>> handle(RequestContext context, Frame<JsonElement> request) {
            if (context.platform().chatService().isEmpty()) {
                return CompletableFuture.completedFuture(unsupported(context, request, "chat service unavailable"));
            }
            AiPayloads.AiReplyPayload payload = payload(context, request, AiPayloads.AiReplyPayload.class);
            if ("private".equalsIgnoreCase(payload.delivery().type())) {
                PlayerSelector selector = new PlayerSelector(parseUuid(payload.delivery().playerUuid()), payload.delivery().playerName());
                return context.platform().chatService().get().sendPrivate(selector, toText(payload.message())).thenApply(ignored -> ok(context, request, Map.of("delivered", true)));
            }
            return context.platform().chatService().get().broadcast(toText(payload.message())).thenApply(ignored -> ok(context, request, Map.of("delivered", true)));
        }
    }

    private static final class CommandExecuteHandler extends BaseHandler {
        @Override
        public String op() {
            return Ops.COMMAND_EXECUTE;
        }

        @Override
        public CompletionStage<Frame<?>> handle(RequestContext context, Frame<JsonElement> request) {
            if (context.platform().commandService().isEmpty()) {
                return CompletableFuture.completedFuture(unsupported(context, request, "command service unavailable"));
            }
            CommandPayloads.CommandExecuteRequest payload = payload(context, request, CommandPayloads.CommandExecuteRequest.class);
            String command = stripSlash(payload.command());
            if (!context.config().commandPolicy().enabled()) {
                return CompletableFuture.completedFuture(context.frameFactory().responseTo(request, Status.error(StatusCode.COMMAND_DISABLED, "remote command is disabled"), Map.of()));
            }
            if (!allowedByPolicy(command, context)) {
                return CompletableFuture.completedFuture(context.frameFactory().responseTo(request, Status.error(StatusCode.COMMAND_BLOCKED, "command blocked by policy"), Map.of()));
            }
            PlatformCommandService.CommandExecutionRequest executionRequest = new PlatformCommandService.CommandExecutionRequest(
                command,
                payload.executor().type(),
                payload.executor().playerName(),
                Duration.ofMillis(payload.executionTimeoutMs())
            );
            if (payload.dedupeKey() == null || payload.dedupeKey().isBlank()) {
                return CompletableFuture.completedFuture(context.frameFactory().responseTo(request, Status.error(StatusCode.BAD_REQUEST, "dedupe_key is required"), Map.of()));
            }
            Optional<CommandJobStore.CommandJob> existing = context.commandJobStore().findByDedupeKey(payload.dedupeKey());
            if (existing.isPresent()) {
                return CompletableFuture.completedFuture(responseForDuplicate(context, request, existing.get()));
            }
            if ("queued".equalsIgnoreCase(payload.mode())) {
                String jobId = Ids.randomId();
                long now = System.currentTimeMillis();
                context.commandJobStore().create(new CommandJobStore.CommandJob(
                    jobId,
                    request.id(),
                    payload.dedupeKey(),
                    context.config().serverId(),
                    now,
                    now + payload.executionTimeoutMs() + 60000L,
                    "pending",
                    StatusCode.OK,
                    "",
                    0,
                    0L
                ));
                context.platform().commandService().get().execute(executionRequest).whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        context.commandJobStore().fail(jobId, StatusCode.INTERNAL_ERROR, throwable.getMessage());
                        context.eventPublisher().publish(Ops.COMMAND_COMPLETED, Target.server(context.config().serverId()), new CommandPayloads.CommandCompletedPayload(jobId, request.id(), context.config().serverId(), false, false, 0L, throwable.getMessage(), 1, System.currentTimeMillis()));
                        return;
                    }
                    context.commandJobStore().complete(jobId, result.output(), result.exitCode(), result.durationMillis());
                    context.eventPublisher().publish(Ops.COMMAND_COMPLETED, Target.server(context.config().serverId()), new CommandPayloads.CommandCompletedPayload(jobId, request.id(), context.config().serverId(), result.accepted(), result.executed(), result.durationMillis(), result.output(), result.exitCode(), System.currentTimeMillis()));
                });
                return CompletableFuture.completedFuture(ok(context, request, new CommandPayloads.CommandExecuteResponse(
                    true,
                    false,
                    jobId,
                    true,
                    0L,
                    null,
                    "queued",
                    null
                )));
            }
            String jobId = Ids.randomId();
            long now = System.currentTimeMillis();
            context.commandJobStore().create(new CommandJobStore.CommandJob(
                jobId,
                request.id(),
                payload.dedupeKey(),
                context.config().serverId(),
                now,
                now + payload.executionTimeoutMs() + 60000L,
                "pending",
                StatusCode.OK,
                "",
                0,
                0L
            ));
            return context.platform().commandService().get().execute(executionRequest).handle((result, throwable) -> {
                if (throwable != null) {
                    context.commandJobStore().fail(jobId, StatusCode.INTERNAL_ERROR, throwable.getMessage());
                    return context.frameFactory().responseTo(request, Status.error(StatusCode.INTERNAL_ERROR, throwable.getMessage()), Map.of());
                }
                context.commandJobStore().complete(jobId, result.output(), result.exitCode(), result.durationMillis());
                return ok(context, request, new CommandPayloads.CommandExecuteResponse(
                    result.accepted(),
                    result.executed(),
                    null,
                    false,
                    null,
                    result.durationMillis(),
                    result.output(),
                    result.exitCode()
                ));
            });
        }

        private Frame<?> responseForDuplicate(RequestContext context, Frame<JsonElement> request, CommandJobStore.CommandJob job) {
            if ("completed".equals(job.state())) {
                return ok(context, request, new CommandPayloads.CommandExecuteResponse(
                    true,
                    true,
                    job.jobId(),
                    false,
                    null,
                    job.durationMs(),
                    job.output(),
                    job.exitCode()
                ));
            }
            if ("pending".equals(job.state())) {
                return ok(context, request, new CommandPayloads.CommandExecuteResponse(
                    true,
                    false,
                    job.jobId(),
                    true,
                    0L,
                    null,
                    "duplicate command is already pending",
                    null
                ));
            }
            return context.frameFactory().responseTo(request, Status.error(StatusCode.COMMAND_BLOCKED, "duplicate command was already processed"), Map.of("job_id", job.jobId(), "state", job.state()));
        }
    }

    private static final class LogQueryHandler extends BaseHandler {
        @Override
        public String op() {
            return Ops.LOG_QUERY;
        }

        @Override
        public CompletionStage<Frame<?>> handle(RequestContext context, Frame<JsonElement> request) {
            if (context.platform().logService().isEmpty()) {
                return CompletableFuture.completedFuture(unsupported(context, request, "log service unavailable"));
            }
            LogPayloads.LogQueryRequest payload = payload(context, request, LogPayloads.LogQueryRequest.class);
            io.github.railgun19457.astrbotadapter.platform.spi.PlatformLogService.LogQuery query = new io.github.railgun19457.astrbotadapter.platform.spi.PlatformLogService.LogQuery(
                payload.tailLines(), payload.since(), payload.until(), payload.level(), payload.keyword(), payload.maxLines()
            );
            return context.platform().logService().get().query(query).thenApply(result -> ok(context, request, new LogPayloads.LogQueryResponse(
                List.of(new LogPayloads.LogServerItem(
                    context.platform().platformInfo().serverId(),
                    context.platform().platformInfo().role(),
                    Status.success(),
                    result.truncated(),
                    result.lines().stream().map(line -> new LogPayloads.LogLine(line.ts(), line.level(), line.message())).toList()
                )),
                List.of(),
                System.currentTimeMillis()
            )));
        }
    }

    private static final class ConfigSyncHandler extends BaseHandler {
        @Override
        public String op() {
            return Ops.CONFIG_SYNC;
        }

        @Override
        public CompletionStage<Frame<?>> handle(RequestContext context, Frame<JsonElement> request) {
            ConfigPayloads.ConfigSyncPayload payload = payload(context, request, ConfigPayloads.ConfigSyncPayload.class);
            if (payload == null || payload.revision() == null || payload.revision().isBlank()) {
                return CompletableFuture.completedFuture(context.frameFactory().responseTo(
                    request,
                    Status.error(StatusCode.BAD_REQUEST, "config revision is required"),
                    Map.of("applied", false)
                ));
            }
            String serverId = context.config().serverId();
            String previous = APPLIED_CONFIG_REVISIONS.putIfAbsent(serverId, payload.revision());
            if (previous != null && !previous.equals(payload.revision())) {
                return CompletableFuture.completedFuture(context.frameFactory().responseTo(
                    request,
                    Status.error(StatusCode.BAD_REQUEST, "a different config revision is already active; reload the backend before replacing it"),
                    Map.of("applied", false, "revision", previous)
                ));
            }
            context.platform().logger().info("Accepted config sync revision " + payload.revision() + " for next reload");
            return CompletableFuture.completedFuture(ok(context, request, Map.of(
                "applied", true,
                "idempotent", previous != null,
                "revision", payload.revision(),
                "effective_at", "next_reload"
            )));
        }
    }

    private static ServerPayloads.ServerStatusItem toPayload(ServerStatusItem item) {
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L / 1024L;
        long maxMb = runtime.maxMemory() / 1024L / 1024L;
        return new ServerPayloads.ServerStatusItem(
            item.serverId(), item.displayName(), item.role(), item.status(), item.platform(), item.version(), item.onlineCount(), item.maxPlayers(), null,
            new ServerPayloads.Memory(usedMb, maxMb),
            new ServerPayloads.Performance(null, null)
        );
    }

    private static List<ServerPayloads.ServerStatusItem> mergeServerItems(ServerPayloads.ServerStatusItem local, List<ServerPayloads.ServerStatusItem> backends) {
        if (backends.isEmpty()) {
            return List.of(local);
        }
        java.util.ArrayList<ServerPayloads.ServerStatusItem> items = new java.util.ArrayList<>();
        items.add(local);
        items.addAll(backends);
        return List.copyOf(items);
    }

    private static PlayerPayloads.PlayerItem toPayload(PlayerSummary player) {
        return new PlayerPayloads.PlayerItem(player.uuid().toString(), player.name(), player.displayName(), player.serverId(), player.world(), player.gameMode(), player.pingMs());
    }

    private static PlayerPayloads.PlayerDetailItem toPayload(PlayerDetail player) {
        return new PlayerPayloads.PlayerDetailItem(
            player.uuid().toString(), player.name(), player.displayName(), player.online(), player.serverId(), player.world(), player.gameMode(), player.pingMs(),
            player.health(), player.maxHealth(), player.foodLevel(), player.level(), player.exp(), player.operator(), player.allowFlight(),
            player.location() == null ? null : new CommonPayloads.Location(player.location().world(), player.location().x(), player.location().y(), player.location().z(), player.location().yaw(), player.location().pitch()),
            player.firstPlayedAt(), player.lastPlayedAt(), player.onlineDurationMs()
        );
    }

    private static TextOutboundMessage toText(CommonPayloads.TextMessage message) {
        return new TextOutboundMessage(message.content(), Optional.ofNullable(message.plainText()).orElse(message.content()), Optional.ofNullable(message.format()).orElse("plain"), Map.of());
    }

    private static TextOutboundMessage formatExternalMessage(RequestContext context, ChatPayloads.ChatExternalPayload payload) {
        String sender = firstNonBlank(payload.sender().displayName(), payload.sender().name(), payload.sender().id());
        String platform = payload.sender().platform() == null ? "external" : payload.sender().platform();
        String message = Optional.ofNullable(payload.message().plainText()).orElse(payload.message().content());
        String formatted = context.config().chat().externalToMinecraftFormat()
            .replace("{platform}", platform)
            .replace("{sender}", sender)
            .replace("{message}", message);
        return new TextOutboundMessage(formatted, formatted, "plain", Map.of("dedupe_key", payload.dedupeKey()));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "unknown";
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }

    private static String stripSlash(String command) {
        if (command == null) {
            return "";
        }
        return command.startsWith("/") ? command.substring(1) : command;
    }

    private static boolean allowedByPolicy(String command, RequestContext context) {
        String normalized = command.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return false;
        }
        if (!context.config().commandPolicy().allowlist().isEmpty()) {
            boolean allowed = context.config().commandPolicy().allowlist().stream().map(String::toLowerCase).anyMatch(normalized::startsWith);
            if (!allowed) {
                return false;
            }
        }
        return context.config().commandPolicy().denylist().stream().map(String::toLowerCase).noneMatch(normalized::startsWith);
    }
}
