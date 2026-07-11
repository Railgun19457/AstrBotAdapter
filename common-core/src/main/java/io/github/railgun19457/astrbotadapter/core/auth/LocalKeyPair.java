package io.github.railgun19457.astrbotadapter.core.auth;

import java.security.PrivateKey;
import java.security.PublicKey;

public record LocalKeyPair(String keyId, PrivateKey privateKey, PublicKey publicKey, String publicKeyOpenSsh, String fingerprintSha256) {
}
