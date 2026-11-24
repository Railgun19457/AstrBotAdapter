package io.github.railgun19457.astrbotadapter.protocol;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

public final class SecureChannel {
    public static final int MAX_FRAME = 65_536;
    private static final int MAX_PAYLOAD = 47_000;
    private static final Gson JSON = new Gson();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] sendKey;
    private final byte[] receiveKey;
    private long sent;
    private long received;

    private SecureChannel(byte[] sendKey, byte[] receiveKey) {
        this.sendKey = sendKey;
        this.receiveKey = receiveKey;
    }

    public static byte[] transcript(String purpose, String... fields) {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.writeBytes(("astrbot-minecraft/v1/" + purpose + "\0").getBytes(StandardCharsets.UTF_8));
        for (String field : fields) {
            byte[] value = field.getBytes(StandardCharsets.UTF_8);
            result.writeBytes(ByteBuffer.allocate(Integer.BYTES).putInt(value.length).array());
            result.writeBytes(value);
        }
        return result.toByteArray();
    }

    public static SecureChannel derive(PrivateKey ephemeral, String remoteKey, byte[] transcript, byte[] pairing,
                                       boolean server) throws GeneralSecurityException {
        KeyAgreement agreement = KeyAgreement.getInstance("X25519");
        agreement.init(ephemeral);
        agreement.doPhase(publicKey("X25519", remoteKey), true);
        byte[] shared = agreement.generateSecret();
        ByteBuffer input = ByteBuffer.allocate(shared.length + pairing.length).put(shared).put(pairing);
        byte[] salt = MessageDigest.getInstance("SHA-256").digest(transcript);
        byte[] prk = mac(salt, input.array());
        byte[] clientKey = mac(prk, "astrbot-minecraft/v1/client\1".getBytes(StandardCharsets.UTF_8));
        byte[] serverKey = mac(prk, "astrbot-minecraft/v1/server\1".getBytes(StandardCharsets.UTF_8));
        return server ? new SecureChannel(serverKey, clientKey) : new SecureChannel(clientKey, serverKey);
    }

    public String encode(JsonObject message) throws GeneralSecurityException {
        byte[] payload = JSON.toJson(message).getBytes(StandardCharsets.UTF_8);
        if (payload.length > MAX_PAYLOAD) {
            throw new IllegalArgumentException("Payload exceeds the message limit");
        }
        JsonObject sealed = new JsonObject();
        sealed.addProperty("seq", sent);
        sealed.addProperty("ciphertext", base64(crypt(Cipher.ENCRYPT_MODE, sendKey, sent, payload)));
        sent++;
        return JSON.toJson(sealed);
    }

    public JsonObject decode(JsonObject sealed) throws GeneralSecurityException {
        if (!sealed.has("seq") || !sealed.get("seq").isJsonPrimitive()
            || !sealed.getAsJsonPrimitive("seq").isNumber()
            || !Long.toString(received).equals(sealed.get("seq").getAsString())) {
            throw new GeneralSecurityException("Unexpected message sequence");
        }
        byte[] payload = crypt(Cipher.DECRYPT_MODE, receiveKey, received, bytes(text(sealed, "ciphertext", MAX_FRAME)));
        if (payload.length > MAX_PAYLOAD) {
            throw new GeneralSecurityException("Payload exceeds the message limit");
        }
        JsonObject result = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
        received++;
        return result;
    }

    private static byte[] crypt(int mode, byte[] key, long seq, byte[] message) throws GeneralSecurityException {
        byte[] nonce = ByteBuffer.allocate(12).putInt(0).putLong(seq).array();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(ByteBuffer.allocate(Long.BYTES).putLong(seq).array());
        return cipher.doFinal(message);
    }

    public static byte[] mac(byte[] key, byte[] input) throws GeneralSecurityException {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(key, "HmacSHA256"));
        return hmac.doFinal(input);
    }

    public static KeyPair keyPair(String algorithm) throws GeneralSecurityException {
        return KeyPairGenerator.getInstance(algorithm).generateKeyPair();
    }

    public static PublicKey publicKey(String algorithm, String value) throws GeneralSecurityException {
        return KeyFactory.getInstance(algorithm).generatePublic(new X509EncodedKeySpec(bytes(value)));
    }

    public static String sign(PrivateKey key, byte[] message) throws GeneralSecurityException {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(key);
        signature.update(message);
        return base64(signature.sign());
    }

    public static boolean verify(PublicKey key, byte[] message, String proof) throws GeneralSecurityException {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initVerify(key);
        signature.update(message);
        return signature.verify(bytes(proof));
    }

    public static String fingerprint(String publicKey) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes(publicKey)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static String randomToken(int size) {
        byte[] value = new byte[size];
        RANDOM.nextBytes(value);
        return base64(value);
    }

    public static String base64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public static byte[] bytes(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    public static String text(JsonObject value, String key, int limit) {
        if (!value.has(key) || !value.get(key).isJsonPrimitive() || !value.getAsJsonPrimitive(key).isString()) {
            throw new IllegalArgumentException("Missing text field: " + key);
        }
        String result = value.get(key).getAsString();
        if (result.length() > limit) {
            throw new IllegalArgumentException("Text field exceeds its limit: " + key);
        }
        return result;
    }

    public static String nodeId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException("Invalid node ID");
        }
        return value;
    }
}
