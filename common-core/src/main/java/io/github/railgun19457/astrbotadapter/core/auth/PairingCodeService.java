package io.github.railgun19457.astrbotadapter.core.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class PairingCodeService {
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, PairingCode> codes = new ConcurrentHashMap<>();

    public PairingCode create(String targetId, Duration ttl) {
        byte[] bytes = new byte[12];
        secureRandom.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        PairingCode pairingCode = new PairingCode(code, targetId, System.currentTimeMillis() + ttl.toMillis(), false);
        codes.put(code, pairingCode);
        return pairingCode;
    }

    public Optional<PairingCode> verifyAndUse(String code, String targetId) {
        VerificationResult result = verifyAndUseDetailed(code, targetId);
        return result.status() == VerificationStatus.OK ? Optional.of(result.code()) : Optional.empty();
    }

    public VerificationResult verifyAndUseDetailed(String code, String targetId) {
        if (code == null || code.isBlank()) {
            return new VerificationResult(VerificationStatus.MISSING, null);
        }
        VerificationResult[] result = new VerificationResult[1];
        codes.compute(code, (ignored, pairingCode) -> {
            if (pairingCode == null) {
                result[0] = new VerificationResult(VerificationStatus.MISSING, null);
                return null;
            }
            if (pairingCode.used()) {
                result[0] = new VerificationResult(VerificationStatus.USED, pairingCode);
                return pairingCode;
            }
            if (pairingCode.expiresAt() < System.currentTimeMillis()) {
                result[0] = new VerificationResult(VerificationStatus.EXPIRED, pairingCode);
                return pairingCode;
            }
            if (targetId != null && pairingCode.targetId() != null && !targetId.equals(pairingCode.targetId())) {
                result[0] = new VerificationResult(VerificationStatus.TARGET_MISMATCH, pairingCode);
                return pairingCode;
            }
            PairingCode used = new PairingCode(pairingCode.code(), pairingCode.targetId(), pairingCode.expiresAt(), true);
            result[0] = new VerificationResult(VerificationStatus.OK, used);
            return used;
        });
        return result[0];
    }

    public Map<String, PairingCode> list() {
        return Map.copyOf(codes);
    }

    public boolean revoke(String code) {
        return codes.remove(code) != null;
    }

    public record PairingCode(String code, String targetId, long expiresAt, boolean used) {
    }

    public enum VerificationStatus {
        OK,
        MISSING,
        USED,
        EXPIRED,
        TARGET_MISMATCH
    }

    public record VerificationResult(VerificationStatus status, PairingCode code) {
    }
}
