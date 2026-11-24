package io.github.railgun19457.astrbotadapter.velocity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.railgun19457.astrbotadapter.common.EndpointHost.Settings;
import io.github.railgun19457.astrbotadapter.transport.AdapterServer.RequestFailure;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static io.github.railgun19457.astrbotadapter.common.EndpointHost.*;

record NativeBackend(Settings settings, String id, String host) {
    private static final int TIMEOUT_MS = 1250;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(TIMEOUT_MS)).build();

    private String setting(String key) {
        return settings.property("backends." + id + "." + key, "");
    }

    boolean queryEnabled() {
        return !setting("query.port").isBlank();
    }

    boolean managementEnabled() {
        return !setting("management.url").isBlank();
    }

    boolean rconEnabled() {
        return !setting("rcon.port").isBlank();
    }

    Set<String> rconCommands() {
        return commandRoots(setting("rcon.allow"));
    }

    JsonObject rcon(String command) {
        String password = setting("rcon.password");
        if (password.isBlank() || password.length() > 128 || password.indexOf('\0') >= 0) {
            throw new RequestFailure("rcon_config", "Configure a valid RCON password for this backend");
        }
        boolean dispatched = false;
        try (Socket socket = new Socket()) {
            socket.connect(address("rcon"), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);
            var out = socket.getOutputStream();
            var in = new DataInputStream(socket.getInputStream());
            out.write(packet(1, 3, password));
            Packet auth = readPacket(in);
            if (auth.id == 1 && auth.type == 0) {
                auth = readPacket(in);
            }
            if (auth.id != 1 || auth.type != 2) {
                throw new RequestFailure("rcon_auth", "Backend RCON rejected authentication");
            }
            dispatched = true;
            out.write(packet(2, 2, command));
            // Minecraft returns a packet for an unknown request type. Its ID marks the end of command output.
            var output = new ByteArrayOutputStream();
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MS);
            for (int count = 0; count < 64 && System.nanoTime() < deadline; count++) {
                Packet response = readPacket(in);
                if (response.id == 3) {
                    String text = output.toString(StandardCharsets.UTF_8);
                    return object(Map.of("accepted", true, "output", text.lines().limit(16)
                        .map(line -> limit(line, 320)).toList(), "source", "rcon"));
                }
                if (response.id != 2 || response.type != 0) {
                    throw new IOException("Unexpected RCON response");
                }
                if (count == 0) {
                    out.write(packet(3, 0, ""));
                }
                if (output.size() < 16_384) {
                    output.writeBytes(response.body);
                }
            }
            throw new IOException("Incomplete RCON response");
        } catch (IOException failure) {
            throw new RequestFailure(dispatched ? "command_unknown" : "backend_offline",
                dispatched ? "The command result is unknown" : "Could not reach backend RCON");
        }
    }

    private static byte[] packet(int id, int type, String value) {
        byte[] body = value.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(body.length + 14).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(body.length + 10).putInt(id).putInt(type).put(body).putShort((short) 0).array();
    }

    private static Packet readPacket(DataInputStream input) throws IOException {
        int length = Integer.reverseBytes(input.readInt());
        if (length < 10 || length > 8192) {
            throw new IOException("Invalid RCON packet size");
        }
        int id = Integer.reverseBytes(input.readInt());
        int type = Integer.reverseBytes(input.readInt());
        byte[] body = new byte[length - 10];
        input.readFully(body);
        if (input.readUnsignedShort() != 0) {
            throw new IOException("Invalid RCON packet footer");
        }
        return new Packet(id, type, body);
    }

    private record Packet(int id, int type, byte[] body) {}

    JsonObject query() throws IOException {
        try (var socket = new DatagramSocket()) {
            socket.connect(address("query"));
            socket.setSoTimeout(TIMEOUT_MS);
            int session = ThreadLocalRandom.current().nextInt() & 0x0f0f0f0f;
            var request = ByteBuffer.allocate(15).putShort((short) 0xfefd).put((byte) 9).putInt(session);
            socket.send(new DatagramPacket(request.array(), 7));
            byte[] challenge = reply(socket, 9, session);
            if (challenge[challenge.length - 1] != 0) {
                throw new IOException("Invalid Query challenge");
            }
            int token = Integer.parseInt(new String(challenge, 5, challenge.length - 6, StandardCharsets.US_ASCII));
            request.put(2, (byte) 0).putInt(token).putInt(0);
            socket.send(new DatagramPacket(request.array(), 15));
            byte[] result = reply(socket, 0, session);
            String value = new String(result, StandardCharsets.UTF_8);
            int separator = value.indexOf("\0\0\1player_\0\0", 16);
            if (separator < 0 || !value.startsWith("splitnum\0", 5)) {
                throw new IOException("Invalid Query full-stat reply");
            }
            var info = new JsonObject();
            String[] fields = value.substring(16, separator).split("\0");
            for (int i = 0; i + 1 < fields.length; i += 2) {
                String field = fields[i + 1];
                switch (fields[i]) {
                    case "hostname" -> info.addProperty("motd", limit(field, 200));
                    case "numplayers" -> info.addProperty("reported_online", Integer.parseInt(field));
                    case "maxplayers" -> info.addProperty("capacity", Integer.parseInt(field));
                    case "version" -> info.addProperty("minecraft", limit(field, 128));
                    default -> { }
                }
            }
            List<JsonObject> players = Arrays.stream(value.substring(separator + 11).split("\0"))
                .filter(name -> name.matches("[A-Za-z0-9_.]{1,32}")).limit(250)
                .map(name -> object(Map.of("name", name, "server", id))).toList();
            info.add("players", object(Map.of("total", info.has("reported_online") ? info.get("reported_online").getAsInt() : players.size(),
                "players", players, "source", "query")));
            return info;
        }
    }

    private static byte[] reply(DatagramSocket socket, int type, int session) throws IOException {
        byte[] buffer = new byte[8192];
        var reply = new DatagramPacket(buffer, buffer.length);
        socket.receive(reply);
        if (reply.getLength() <= 5 || buffer[0] != type || ByteBuffer.wrap(buffer, 1, 4).getInt() != session) {
            throw new IOException("Invalid Query response header");
        }
        return Arrays.copyOf(buffer, reply.getLength());
    }

    JsonElement management(String method) throws Exception {
        URI uri = URI.create(setting("management.url"));
        String scheme = uri.getScheme();
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getFragment() != null
                || !("wss".equals(scheme) || "ws".equals(scheme) && Arrays.stream(InetAddress.getAllByName(uri.getHost()))
                    .allMatch(InetAddress::isLoopbackAddress))) {
            throw new IOException("Management URL must use TLS or loopback");
        }
        String secret = setting("management.secret");
        if (!secret.matches("[a-zA-Z0-9]{40}")) {
            throw new IOException("Invalid management server secret");
        }
        var replies = new ArrayBlockingQueue<JsonObject>(8);
        var socket = HTTP.newWebSocketBuilder().connectTimeout(Duration.ofMillis(TIMEOUT_MS))
            .header("Authorization", "Bearer " + secret).buildAsync(uri, new WebSocket.Listener() {
                private final StringBuilder text = new StringBuilder();

                @Override
                public java.util.concurrent.CompletionStage<?> onText(WebSocket socket, CharSequence chunk, boolean last) {
                    if (text.length() + chunk.length() > 524_288) {
                        socket.abort();
                        return null;
                    }
                    text.append(chunk);
                    if (last) {
                        var reply = JsonParser.parseString(text.toString()).getAsJsonObject();
                        text.setLength(0);
                        if (reply.has("id")) {
                            replies.offer(reply);
                        }
                    }
                    socket.request(1);
                    return null;
                }
            }).orTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS).join();
        try {
            var schema = rpc(socket, replies, 1, "rpc.discover").getAsJsonObject();
            for (var descriptor : schema.getAsJsonArray("methods")) {
                if (method.equals(descriptor.getAsJsonObject().get("name").getAsString())) {
                    return rpc(socket, replies, 2, method);
                }
            }
            throw new IOException("Management method unavailable");
        } finally {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "").orTimeout(300, TimeUnit.MILLISECONDS)
                .whenComplete((result, failure) -> socket.abort());
        }
    }

    private static JsonElement rpc(WebSocket socket, ArrayBlockingQueue<JsonObject> replies, int id, String method) throws Exception {
        socket.sendText(object(Map.of("jsonrpc", "2.0", "id", id, "method", method)).toString(), true)
            .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        JsonObject response = replies.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (response == null || response.get("id").getAsInt() != id || !response.has("result")) {
            throw new IOException("Management request failed");
        }
        return response.get("result");
    }

    private InetSocketAddress address(String type) {
        int port = Integer.parseInt(setting(type + ".port"));
        return new InetSocketAddress(host, port);
    }
}
