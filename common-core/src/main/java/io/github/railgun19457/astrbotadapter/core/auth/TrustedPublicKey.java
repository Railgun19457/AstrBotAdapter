package io.github.railgun19457.astrbotadapter.core.auth;

import java.security.PublicKey;

public record TrustedPublicKey(String domain, String keyId, PublicKey publicKey, String openSsh, String fingerprintSha256) {
    public TrustedPublicKey(String keyId, PublicKey publicKey, String openSsh, String fingerprintSha256) {
        this("", keyId, publicKey, openSsh, fingerprintSha256);
    }
}
