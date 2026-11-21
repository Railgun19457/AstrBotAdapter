package io.github.railgun19457.astrbotadapter.protocol.payload;

import io.github.railgun19457.astrbotadapter.protocol.Status;

import java.util.List;

public final class AuthPayloads {
    private AuthPayloads() {
    }

    public record ProtocolRange(String name, int minV, int maxV) {
    }

    public record AuthHelloPayload(ProtocolRange protocol, String keyId, String clientNonce, List<String> capabilities) {
    }

    public record AuthChallengePayload(String serverKeyId, String serverNonce, String challengeId, long expiresAt, int selectedV, List<String> capabilities) {
    }

    public record AuthPayload(String keyId, String publicKey, String signature) {
    }

    public record AuthResultPayload(boolean ok, String keyId, String publicKey, String signature, String sessionId, Long serverTime, List<String> capabilities, Status status, String trustMode) {
    }

    public record PairInitPayload(ProtocolRange protocol, String keyId, String publicKey, String pairingId, List<String> capabilities) {
    }

    public record PairChallengePayload(String pairingId, String suite, String salt, String serverPublicValue, String serverKeyId, String serverPublicKey, long expiresAt, int selectedV, List<String> capabilities) {
    }

    public record PairProofPayload(String keyId, String publicKey, String clientPublicValue, String clientProof) {
    }

    public record PairCompletePayload(boolean ok, String keyId, String publicKey, String serverProof, String keyBinding, String sessionId, Long serverTime, List<String> capabilities, Status status, String trustMode) {
    }
}
