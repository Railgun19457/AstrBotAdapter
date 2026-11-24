package io.github.railgun19457.astrbotadapter.transport;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.railgun19457.astrbotadapter.protocol.SecureChannel;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TrustStore {
    private static final Duration PAIR_LIFETIME = Duration.ofMinutes(10);

    private final Path trustFile;
    private final KeyPair identity;
    private final Map<String, Pairing> pairings = new HashMap<>();
    private final JsonObject trusted;

    public TrustStore(Path directory) throws IOException, GeneralSecurityException {
        Files.createDirectories(directory);
        Path keyFile = directory.resolve("identity.json");
        trustFile = directory.resolve("trusted.json");
        if (Files.exists(keyFile)) {
            JsonObject stored = read(keyFile);
            identity = new KeyPair(
                SecureChannel.publicKey("Ed25519", SecureChannel.text(stored, "public", 128)),
                KeyFactory.getInstance("Ed25519").generatePrivate(
                    new PKCS8EncodedKeySpec(SecureChannel.bytes(SecureChannel.text(stored, "private", 128))))
            );
        } else {
            if (Files.exists(trustFile)) {
                throw new IOException("The server identity is missing");
            }
            identity = SecureChannel.keyPair("Ed25519");
            JsonObject stored = new JsonObject();
            stored.addProperty("public", SecureChannel.base64(identity.getPublic().getEncoded()));
            stored.addProperty("private", SecureChannel.base64(identity.getPrivate().getEncoded()));
            write(keyFile, stored);
        }
        trusted = Files.exists(trustFile) ? read(trustFile) : new JsonObject();
    }

    public KeyPair identity() {
        return identity;
    }

    public synchronized Pairing create(String client) {
        SecureChannel.nodeId(client);
        if (trusted.has(client)) {
            throw new IllegalArgumentException("Revoke the existing trust before pairing this node again");
        }
        Instant now = Instant.now();
        pairings.values().removeIf(entry -> entry.expiry().isBefore(now) || entry.client().equals(client));
        if (pairings.size() >= 64) {
            throw new IllegalArgumentException("Too many outstanding pairing codes");
        }
        Pairing pairing = new Pairing(SecureChannel.randomToken(16), SecureChannel.randomToken(24), client,
            now.plus(PAIR_LIFETIME));
        pairings.put(pairing.id(), pairing);
        return pairing;
    }

    public synchronized Pairing find(String id, String client) {
        Pairing entry = pairings.get(id);
        return entry != null && entry.client().equals(client) && entry.expiry().isAfter(Instant.now()) ? entry : null;
    }

    public synchronized boolean cancel(String id) {
        return pairings.remove(id) != null;
    }

    public synchronized boolean trusted(String client, String key) {
        return key.equals(keyFor(client));
    }

    public synchronized String keyFor(String client) {
        return trusted.has(client) ? trusted.get(client).getAsString() : null;
    }

    public synchronized void pin(String server, String key) throws IOException {
        if (key.equals(keyFor(server))) {
            return;
        }
        if (trusted.has(server)) {
            throw new IllegalArgumentException("The server identity has changed");
        }
        storeTrust(server, key);
    }

    public synchronized void enroll(Pairing pairing, String key) throws IOException {
        if (find(pairing.id(), pairing.client()) != pairing || trusted.has(pairing.client())) {
            throw new IllegalStateException("Pairing is no longer available");
        }
        storeTrust(pairing.client(), key);
        pairings.remove(pairing.id());
    }

    private void storeTrust(String node, String key) throws IOException {
        JsonObject updated = trusted.deepCopy();
        updated.addProperty(node, key);
        write(trustFile, updated);
        trusted.addProperty(node, key);
    }

    public synchronized boolean revoke(String client) throws IOException {
        if (!trusted.has(client)) {
            return false;
        }
        JsonObject updated = trusted.deepCopy();
        updated.remove(client);
        write(trustFile, updated);
        trusted.remove(client);
        return true;
    }

    public synchronized List<String> clients() {
        return new ArrayList<>(trusted.keySet());
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private static void write(Path file, JsonObject value) throws IOException {
        Path temporary = Files.createTempFile(file.getParent(), ".state-", ".tmp");
        try {
            if (Files.getFileStore(temporary).supportsFileAttributeView("posix")) {
                Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------"));
            }
            Files.writeString(temporary, value.toString());
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public record Pairing(String id, String code, String client, Instant expiry) {}
}
