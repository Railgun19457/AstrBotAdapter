package io.github.railgun19457.astrbotadapter.core.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

public final class CryptoUtil {
    private static final byte[] ED25519_SSH_PREFIX = new byte[]{0, 0, 0, 11, 115, 115, 104, 45, 101, 100, 50, 53, 53, 49, 57, 0, 0, 0, 32};
    private static final byte[] ED25519_X509_PREFIX = new byte[]{0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00};

    private CryptoUtil() {
    }

    public static KeyPair generateEd25519() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
            return generator.generateKeyPair();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Ed25519 is not available", exception);
        }
    }

    public static PrivateKey privateKeyFromBase64(String value) throws GeneralSecurityException {
        return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(value)));
    }

    public static PublicKey publicKeyFromBase64(String value) throws GeneralSecurityException {
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(value)));
    }

    public static PublicKey publicKeyFromRawEd25519(byte[] raw) throws GeneralSecurityException {
        if (raw == null || raw.length != 32) {
            throw new GeneralSecurityException("invalid raw Ed25519 public key length");
        }
        byte[] x509 = new byte[ED25519_X509_PREFIX.length + raw.length];
        System.arraycopy(ED25519_X509_PREFIX, 0, x509, 0, ED25519_X509_PREFIX.length);
        System.arraycopy(raw, 0, x509, ED25519_X509_PREFIX.length, raw.length);
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(x509));
    }

    public static PublicKey publicKeyFromText(String value) throws GeneralSecurityException {
        if (value == null || value.isBlank()) {
            throw new GeneralSecurityException("public key is empty");
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("ssh-ed25519 ")) {
            return publicKeyFromOpenSsh(trimmed);
        }
        return publicKeyFromBase64(trimmed);
    }

    public static PublicKey publicKeyFromOpenSsh(String value) throws GeneralSecurityException {
        String[] parts = value.trim().split("\\s+");
        if (parts.length < 2 || !"ssh-ed25519".equals(parts[0])) {
            throw new GeneralSecurityException("not an ssh-ed25519 public key");
        }
        byte[] blob = Base64.getDecoder().decode(parts[1]);
        if (blob.length != ED25519_SSH_PREFIX.length + 32) {
            throw new GeneralSecurityException("invalid ssh-ed25519 key length");
        }
        for (int index = 0; index < ED25519_SSH_PREFIX.length; index++) {
            if (blob[index] != ED25519_SSH_PREFIX[index]) {
                throw new GeneralSecurityException("invalid ssh-ed25519 key prefix");
            }
        }
        byte[] raw = Arrays.copyOfRange(blob, ED25519_SSH_PREFIX.length, blob.length);
        return publicKeyFromRawEd25519(raw);
    }

    public static String privateKeyToBase64(PrivateKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static String publicKeyToBase64(PublicKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static byte[] rawEd25519PublicKey(PublicKey key) {
        byte[] encoded = key.getEncoded();
        return Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length);
    }

    public static String fingerprintSha256(PublicKey key) {
        return base64Url(sha256(rawEd25519PublicKey(key)));
    }

    public static String sha256Base64Url(String value) {
        return base64Url(sha256(value.getBytes(StandardCharsets.UTF_8)));
    }

    public static String signatureSha256(byte[] signature) {
        return base64Url(sha256(signature));
    }

    public static String publicKeyOpenSsh(PublicKey key, String keyId) {
        byte[] raw = rawEd25519PublicKey(key);
        byte[] blob = new byte[ED25519_SSH_PREFIX.length + raw.length];
        System.arraycopy(ED25519_SSH_PREFIX, 0, blob, 0, ED25519_SSH_PREFIX.length);
        System.arraycopy(raw, 0, blob, ED25519_SSH_PREFIX.length, raw.length);
        return "ssh-ed25519 " + Base64.getEncoder().encodeToString(blob) + " " + keyId;
    }

    public static byte[] sign(PrivateKey privateKey, String transcript) throws GeneralSecurityException {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(privateKey);
        signature.update(transcript.getBytes(StandardCharsets.UTF_8));
        return signature.sign();
    }

    public static boolean verify(PublicKey publicKey, String transcript, byte[] signatureBytes) throws GeneralSecurityException {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initVerify(publicKey);
        signature.update(transcript.getBytes(StandardCharsets.UTF_8));
        return signature.verify(signatureBytes);
    }

    public static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public static byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    public static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
