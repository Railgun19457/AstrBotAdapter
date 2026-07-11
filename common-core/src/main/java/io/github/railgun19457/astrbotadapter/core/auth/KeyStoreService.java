package io.github.railgun19457.astrbotadapter.core.auth;

import io.github.railgun19457.astrbotadapter.platform.spi.PlatformAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class KeyStoreService {
    private final PlatformAdapter platform;

    public KeyStoreService(PlatformAdapter platform) {
        this.platform = platform;
    }

    public LocalKeyPair loadOrCreateLocalKey(String keyId) {
        Path keys = platform.keyStore().keysDirectory();
        Path privatePath = keys.resolve(keyId + ".key");
        Path publicPath = keys.resolve(keyId + ".pub");
        try {
            Files.createDirectories(keys);
            if (Files.exists(privatePath)) {
                String encoded = Files.readString(privatePath).trim();
                var privateKey = CryptoUtil.privateKeyFromBase64(encoded);
                PublicKey publicKey;
                if (Files.exists(publicPath)) {
                    publicKey = CryptoUtil.publicKeyFromText(Files.readString(publicPath).lines().findFirst().orElseThrow().trim());
                } else {
                    KeyPair generated = CryptoUtil.generateEd25519();
                    publicKey = generated.getPublic();
                    platform.logger().warn("Public key missing for " + keyId + "; regenerated public metadata from a new key is not possible, creating replacement pair");
                    return create(keyId, privatePath, publicPath);
                }
                return new LocalKeyPair(keyId, privateKey, publicKey, CryptoUtil.publicKeyOpenSsh(publicKey, keyId), CryptoUtil.fingerprintSha256(publicKey));
            }
            return create(keyId, privatePath, publicPath);
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalStateException("failed to load local key", exception);
        }
    }

    public List<TrustedPublicKey> trustedKeys(String domain) {
        Path directory = platform.keyStore().keysDirectory().resolve(domain);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<TrustedPublicKey> keys = new ArrayList<>();
        try (var stream = Files.list(directory)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".pub")).forEach(path -> readTrusted(path).ifPresent(keys::add));
        } catch (IOException exception) {
            platform.logger().error("Failed to read trusted keys", exception);
        }
        return List.copyOf(keys);
    }

    public void trust(String domain, String keyId, String publicKeyBase64) {
        try {
            Path directory = platform.keyStore().keysDirectory().resolve(domain);
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(keyId + ".pub"), publicKeyBase64 + System.lineSeparator());
        } catch (IOException exception) {
            throw new IllegalStateException("failed to save trusted key", exception);
        }
    }

    public List<TrustedPublicKey> allTrustedKeys() {
        List<TrustedPublicKey> all = new ArrayList<>();
        for (String domain : List.of("astrbot", "velocity", "backends", "standalone")) {
            for (TrustedPublicKey key : trustedKeys(domain)) {
                all.add(new TrustedPublicKey(domain, key.keyId(), key.publicKey(), key.openSsh(), key.fingerprintSha256()));
            }
        }
        return List.copyOf(all);
    }

    public boolean removeTrustedKey(String domain, String keyId) {
        if (domain == null || domain.isBlank() || keyId == null || keyId.isBlank()) {
            return false;
        }
        Path path = platform.keyStore().keysDirectory().resolve(domain).resolve(keyId + ".pub");
        try {
            return Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to remove trusted key", exception);
        }
    }

    public LocalKeyPair regenerateLocalKey(String keyId) {
        Path keys = platform.keyStore().keysDirectory();
        Path privatePath = keys.resolve(keyId + ".key");
        Path publicPath = keys.resolve(keyId + ".pub");
        try {
            Files.createDirectories(keys);
            backupIfExists(privatePath);
            backupIfExists(publicPath);
            return create(keyId, privatePath, publicPath);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to regenerate local key", exception);
        }
    }

    private void backupIfExists(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.copy(path, path.resolveSibling(path.getFileName() + ".bak"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private LocalKeyPair create(String keyId, Path privatePath, Path publicPath) throws IOException {
        KeyPair keyPair = CryptoUtil.generateEd25519();
        Files.writeString(privatePath, CryptoUtil.privateKeyToBase64(keyPair.getPrivate()) + System.lineSeparator());
        Files.writeString(publicPath, CryptoUtil.publicKeyToBase64(keyPair.getPublic()) + System.lineSeparator());
        return new LocalKeyPair(keyId, keyPair.getPrivate(), keyPair.getPublic(), CryptoUtil.publicKeyOpenSsh(keyPair.getPublic(), keyId), CryptoUtil.fingerprintSha256(keyPair.getPublic()));
    }

    private java.util.Optional<TrustedPublicKey> readTrusted(Path path) {
        try {
            String keyId = path.getFileName().toString().replaceFirst("\\.pub$", "");
            PublicKey publicKey = CryptoUtil.publicKeyFromText(Files.readString(path).lines().findFirst().orElseThrow().trim());
            return Optional.of(new TrustedPublicKey(keyId, publicKey, CryptoUtil.publicKeyOpenSsh(publicKey, keyId), CryptoUtil.fingerprintSha256(publicKey)));
        } catch (Exception exception) {
            platform.logger().warn("Skipping invalid trusted key " + path.getFileName());
            return Optional.empty();
        }
    }
}
