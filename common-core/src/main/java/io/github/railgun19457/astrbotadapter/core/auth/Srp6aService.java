package io.github.railgun19457.astrbotadapter.core.auth;

import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.agreement.srp.SRP6Server;
import org.bouncycastle.crypto.agreement.srp.SRP6StandardGroups;
import org.bouncycastle.crypto.agreement.srp.SRP6VerifierGenerator;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.params.SRP6GroupParameters;
import org.bouncycastle.util.BigIntegers;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * Provides the fixed SRP-6a suite used for one-time pairing.
 *
 * <p>The wire format uses unsigned, minimally encoded big-endian integers encoded as
 * base64url without padding. The evidence messages use pysrp RFC 5054 compatible encoding
 * for interoperability with Python {@code srp} configured for {@code SHA256}/{@code NG_2048}.</p>
 */
public final class Srp6aService {
    public static final String SUITE = "srp-6a-rfc5054-sha256-2048";
    private static final SRP6GroupParameters GROUP = SRP6StandardGroups.rfc5054_2048;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private Srp6aService() {
    }

    /**
     * Creates a verifier that can authenticate one holder of a pairing code.
     *
     * @param identity Stable identity binding for the pairing target.
     * @param code High-entropy, one-time pairing code.
     * @return Newly generated SRP salt and verifier.
     */
    public static Registration register(String identity, String code) {
        byte[] salt = new byte[32];
        SECURE_RANDOM.nextBytes(salt);
        SRP6VerifierGenerator generator = new SRP6VerifierGenerator();
        generator.init(GROUP, new SHA256Digest());
        BigInteger verifier = generator.generateVerifier(
            salt,
            utf8(identity),
            utf8(code)
        );
        return new Registration(base64Url(salt), encode(verifier));
    }

    /**
     * Starts a server-side SRP-6a authentication exchange.
     *
     * @param registration Stored one-time pairing registration.
     * @return Server exchange state and public value {@code B}.
     */
    public static ServerSession startServer(Registration registration) {
        return startServer(registration, SECURE_RANDOM);
    }

    static ServerSession startServer(Registration registration, SecureRandom random) {
        SRP6Server server = new SRP6Server();
        server.init(
            GROUP,
            decode(registration.verifier()),
            new SHA256Digest(),
            random
        );
        BigInteger publicValue = server.generateServerCredentials();
        return new ServerSession(server, base64UrlDecode(registration.salt()), encode(publicValue));
    }

    /**
    * Verifies the client proof and returns the matching server proof and key material.
     *
     * @param session Active server-side exchange state.
    * @param clientPublicValue Client SRP public value {@code A}.
    * @param identity Stable identity binding for the pairing target.
    * @param clientProof Client evidence value {@code M1}.
    * @return Server proof result, or {@code null} if verification fails.
     * @throws CryptoException If the session state is invalid.
     */
    public static ServerProofResult verifyClientProof(ServerSession session, String clientPublicValue, String identity, String clientProof) throws CryptoException {
        BigInteger clientPublic = decode(clientPublicValue);
        BigInteger sharedSecret = session.server().calculateSecret(clientPublic);
        byte[] sessionKey = sessionKey(sharedSecret);
        byte[] expectedClientProof = clientEvidence(identity, session.salt(), clientPublic, decode(session.serverPublicValue()), sessionKey);
        if (!MessageDigest.isEqual(expectedClientProof, base64UrlDecode(clientProof))) {
            return null;
        }
        return new ServerProofResult(base64Url(serverEvidence(clientPublic, expectedClientProof, sessionKey)), sessionKey);
    }

    /**
     * Starts a client-side SRP-6a exchange.
     *
     * @param identity Stable identity binding for the pairing target.
     * @param code High-entropy, one-time pairing code.
     * @return Client exchange state and public value {@code A}.
     */
    public static ClientSession createClientProof(String identity, String code, String salt, String serverPublicValue) throws CryptoException {
        org.bouncycastle.crypto.agreement.srp.SRP6Client client = new org.bouncycastle.crypto.agreement.srp.SRP6Client();
        client.init(GROUP, new SHA256Digest(), SECURE_RANDOM);
        byte[] decodedSalt = base64UrlDecode(salt);
        BigInteger clientPublic = client.generateClientCredentials(decodedSalt, utf8(identity), utf8(code));
        BigInteger serverPublic = decode(serverPublicValue);
        BigInteger sharedSecret = client.calculateSecret(serverPublic);
        byte[] sessionKey = sessionKey(sharedSecret);
        byte[] clientProof = clientEvidence(identity, decodedSalt, clientPublic, serverPublic, sessionKey);
        return new ClientSession(encode(clientPublic), base64Url(clientProof), base64Url(serverEvidence(clientPublic, clientProof, sessionKey)), sessionKey);
    }

    /**
     * Verifies the server proof after the client proof was created.
     *
     * @param session Completed client-side exchange state.
     * @param serverProof Server evidence value {@code M2}.
     * @return Whether the proof is valid.
     */
    public static boolean verifyServerProof(ClientSession session, String serverProof) {
        return MessageDigest.isEqual(base64UrlDecode(session.expectedServerProof()), base64UrlDecode(serverProof));
    }

    /**
     * Creates a confirmation that binds the mutually authenticated SRP secret to both
     * long-term public keys and the complete pairing transcript.
     *
     * @param sessionKey SRP session key $K$.
     * @param pairingId Public identifier for the one-time pairing record.
     * @param clientRole Client role wire name.
     * @param clientId Client identifier.
     * @param serverRole Server role wire name.
     * @param serverId Server identifier.
     * @param clientKeyId Client key identifier.
     * @param clientPublicKey Client OpenSSH Ed25519 public key.
     * @param serverKeyId Server key identifier.
     * @param serverPublicKey Server OpenSSH Ed25519 public key.
     * @param salt SRP salt as base64url.
     * @param clientPublicValue SRP public value $A$ as base64url.
     * @param serverPublicValue SRP public value $B$ as base64url.
     * @param clientProof SRP evidence $M1$ as base64url.
     * @param serverProof SRP evidence $M2$ as base64url.
     * @return Base64url HMAC-SHA-256 binding confirmation.
     */
    public static String keyBindingConfirmation(
        byte[] sessionKey,
        String pairingId,
        String clientRole,
        String clientId,
        String serverRole,
        String serverId,
        String clientKeyId,
        String clientPublicKey,
        String serverKeyId,
        String serverPublicKey,
        String salt,
        String clientPublicValue,
        String serverPublicValue,
        String clientProof,
        String serverProof
    ) {
        String transcript = "ASTRBOT-MC-SRP-PAIR-BINDING-V1\n"
            + "pairing_id=" + pairingId + '\n'
            + "client_role=" + clientRole + '\n'
            + "client_id=" + clientId + '\n'
            + "server_role=" + serverRole + '\n'
            + "server_id=" + serverId + '\n'
            + "client_key_id=" + clientKeyId + '\n'
            + "client_public_key_sha256=" + sha256Base64(clientPublicKey) + '\n'
            + "server_key_id=" + serverKeyId + '\n'
            + "server_public_key_sha256=" + sha256Base64(serverPublicKey) + '\n'
            + "salt=" + salt + '\n'
            + "A=" + clientPublicValue + '\n'
            + "B=" + serverPublicValue + '\n'
            + "M1=" + clientProof + '\n'
            + "M2=" + serverProof + '\n';
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sessionKey, "HmacSHA256"));
            return base64Url(mac.doFinal(utf8(transcript)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is not available", exception);
        }
    }

    /**
     * Returns the application identity required by the fixed pairing suite.
     *
     * @param pairingId Public identifier for a generated pairing record.
     * @return RFC 5054 username/identity value.
     */
    public static String pairingIdentity(String pairingId) {
        return "astrbot-minecraft-pairing-v1:" + pairingId;
    }

    private static byte[] clientEvidence(String identity, byte[] salt, BigInteger clientPublic, BigInteger serverPublic, byte[] sessionKey) {
        MessageDigest digest = sha256();
        digest.update(xor(sha256(unsigned(GROUP.getN())), sha256(padded(GROUP.getG()))));
        digest.update(sha256(utf8(identity)));
        digest.update(salt);
        digest.update(unsigned(clientPublic));
        digest.update(unsigned(serverPublic));
        digest.update(sessionKey);
        return digest.digest();
    }

    private static byte[] serverEvidence(BigInteger clientPublic, byte[] clientProof, byte[] sessionKey) {
        MessageDigest digest = sha256();
        digest.update(unsigned(clientPublic));
        digest.update(clientProof);
        digest.update(sessionKey);
        return digest.digest();
    }

    private static byte[] sessionKey(BigInteger sharedSecret) {
        return sha256(unsigned(sharedSecret));
    }

    private static byte[] padded(BigInteger value) {
        return BigIntegers.asUnsignedByteArray((GROUP.getN().bitLength() + 7) / 8, value);
    }

    private static byte[] xor(byte[] left, byte[] right) {
        byte[] result = new byte[left.length];
        for (int index = 0; index < left.length; index++) {
            result[index] = (byte) (left[index] ^ right[index]);
        }
        return result;
    }

    private static byte[] sha256(byte[] value) {
        return sha256().digest(value);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static byte[] utf8(String value) {
        return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
    }

    private static String encode(BigInteger value) {
        return base64Url(unsigned(value));
    }

    private static BigInteger decode(String value) {
        return new BigInteger(1, base64UrlDecode(value));
    }

    private static byte[] unsigned(BigInteger value) {
        return BigIntegers.asUnsignedByteArray(value);
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String sha256Base64(String value) {
        return base64Url(sha256(utf8(value)));
    }

    private static byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    public record Registration(String salt, String verifier) {
    }

    public record ServerSession(SRP6Server server, byte[] salt, String serverPublicValue) {
    }

    public record ClientSession(String clientPublicValue, String clientProof, String expectedServerProof, byte[] sessionKey) {
    }

    public record ServerProofResult(String serverProof, byte[] sessionKey) {
    }
}