package io.github.railgun19457.astrbotadapter.common;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.railgun19457.astrbotadapter.protocol.SecureChannel;
import io.github.railgun19457.astrbotadapter.transport.AdapterServer.RequestFailure;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static io.github.railgun19457.astrbotadapter.common.EndpointHost.*;

final class ServerDiagnostics implements AutoCloseable {
    private static final String UUID_PATTERN = "[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}";
    private static final String NAME_PATTERN = "[a-z0-9_.]{1,32}";
    private final Path profiles;
    private final Logger logger;
    private final ExecutorService io = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("astrbot-data").factory());

    ServerDiagnostics(Path directory, Logger logger) {
        profiles = directory.resolve("offline-players");
        this.logger = logger;
    }

    void save(JsonObject player) {
        String uuid = player.get("uuid").getAsString();
        String name = player.get("name").getAsString().toLowerCase(Locale.ROOT);
        if (!uuid.matches(UUID_PATTERN) || !name.matches(NAME_PATTERN)) {
            return;
        }
        player.remove("ping");
        player.addProperty("offline", true);
        player.addProperty("last_seen", Instant.now().toString());
        io.execute(() -> {
            try {
                Files.createDirectories(profiles);
                write(profiles.resolve(uuid + ".json"), player.toString());
                write(profiles.resolve("name-" + name), uuid);
            } catch (IOException failure) {
                logger.warning("Could not store an offline player snapshot: " + failure.getClass().getSimpleName());
            }
        });
    }

    CompletableFuture<JsonObject> find(String player, boolean assets) {
        String key = player.toLowerCase(Locale.ROOT);
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean uuid = key.matches(UUID_PATTERN);
                if (!uuid && !key.matches(NAME_PATTERN)) {
                    throw new RequestFailure("bad_request", "Enter a player name or UUID");
                }
                String id = uuid ? key : Files.readString(profiles.resolve("name-" + key));
                if (!id.matches(UUID_PATTERN)) {
                    throw new IOException("Invalid player snapshot index");
                }
                var found = JsonParser.parseString(Files.readString(profiles.resolve(id + ".json"))).getAsJsonObject();
                if (!uuid && !found.get("name").getAsString().equalsIgnoreCase(player)) {
                    throw new RequestFailure("player_offline", "No snapshot for this player name");
                }
                if (!assets) {
                    found.remove("skin");
                }
                return found;
            } catch (java.nio.file.NoSuchFileException failure) {
                throw new RequestFailure("player_offline", "No offline snapshot for this player");
            } catch (IOException failure) {
                throw new RequestFailure("snapshot_unavailable", "Could not read the offline snapshot");
            }
        }, io);
    }

    CompletableFuture<JsonObject> tail(JsonObject request) {
        int count = request.has("tail_lines") ? request.get("tail_lines").getAsInt() : 20;
        if (count < 1 || count > 100) {
            throw new IllegalArgumentException("tail_lines must be between 1 and 100");
        }
        String keyword = request.has("keyword") ? SecureChannel.text(request, "keyword", 128).toLowerCase(Locale.ROOT) : "";
        return CompletableFuture.supplyAsync(() -> {
            try (var channel = FileChannel.open(Path.of("logs/latest.log"))) {
                long size = channel.size();
                int length = (int) Math.min(size, 262_144);
                var buffer = ByteBuffer.allocate(length);
                channel.position(size - length);
                while (buffer.hasRemaining() && channel.read(buffer) > 0) { }
                String recent = new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8);
                if (size > length) {
                    int next = recent.indexOf('\n');
                    recent = next < 0 ? "" : recent.substring(next + 1);
                }
                var lines = new ArrayDeque<String>();
                String level = request.has("level") ? SecureChannel.text(request, "level", 16).toUpperCase(Locale.ROOT) : "";
                String levelMarker = "/" + level + "]:";
                recent.lines().filter(line -> !line.contains("pairing_id=") && line.toLowerCase(Locale.ROOT).contains(keyword))
                    .filter(line -> level.isEmpty() || line.contains(levelMarker))
                    .forEach(line -> {
                        if (lines.size() == count) {
                            lines.removeFirst();
                        }
                        lines.addLast(limit(line, 240));
                    });
                var reply = object(Map.of("lines", lines, "bytes_scanned", length));
                if (reply.toString().getBytes(StandardCharsets.UTF_8).length > 40_000) {
                    while (lines.size() > 24) {
                        lines.removeFirst();
                    }
                    reply = object(Map.of("lines", lines, "bytes_scanned", length, "truncated", true));
                }
                return reply;
            } catch (IOException failure) {
                throw new RequestFailure("log_unavailable", "The server log is unavailable");
            }
        }, io);
    }

    static void write(Path file, String data) throws IOException {
        Path tmp = Files.createTempFile(file.getParent(), ".write-", ".tmp");
        try {
            Files.writeString(tmp, data);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException failure) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Override
    public void close() {
        io.shutdown();
        try {
            if (!io.awaitTermination(1, TimeUnit.SECONDS)) {
                io.shutdownNow();
            }
        } catch (InterruptedException failure) {
            io.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
