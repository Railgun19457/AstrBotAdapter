package io.github.railgun19457.astrbotadapter.core.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.railgun19457.astrbotadapter.protocol.Role;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CryptoUtilTest {
    @Test
    void openSshPublicKeyRoundTripsToSameFingerprint() throws Exception {
        KeyPair keyPair = CryptoUtil.generateEd25519();
        String openssh = CryptoUtil.publicKeyOpenSsh(keyPair.getPublic(), "test-key");

        assertEquals(CryptoUtil.fingerprintSha256(keyPair.getPublic()), CryptoUtil.fingerprintSha256(CryptoUtil.publicKeyFromText(openssh)));
    }

    @Test
    void signsAndVerifiesTranscript() throws Exception {
        KeyPair keyPair = CryptoUtil.generateEd25519();
        String transcript = AuthTranscript.clientProof(new AuthTranscript.ClientProof(
            1,
            1,
            1,
            "astrbot-to-standalone",
            Role.ASTRBOT,
            "client",
            Role.STANDALONE,
            "server",
            "client-key",
            "server-key",
            CryptoUtil.fingerprintSha256(keyPair.getPublic()),
            "",
            "client-nonce",
            "server-nonce",
            "challenge",
            1710000030100L,
            "hello",
            "challenge-frame",
            1710000000000L,
            1710000000200L,
            AuthTranscript.capabilitiesSha256(List.of("core.v1")),
            AuthTranscript.capabilitiesSha256(List.of("core.v1"))
        ));

        assertTrue(CryptoUtil.verify(keyPair.getPublic(), transcript, CryptoUtil.sign(keyPair.getPrivate(), transcript)));
    }

    @Test
    void authTranscriptVectorMatchesExpectedStringsAndSignatures() throws Exception {
        JsonObject vector = JsonParser.parseString(Files.readString(Path.of("..", "protocol-contract", "vectors", "auth_transcript_v1.json"))).getAsJsonObject();
        PrivateKey clientPrivateKey = CryptoUtil.privateKeyFromBase64(vector.get("client_private_key_pkcs8_base64").getAsString());
        PrivateKey serverPrivateKey = CryptoUtil.privateKeyFromBase64(vector.get("server_private_key_pkcs8_base64").getAsString());
        PublicKey clientPublicKey = CryptoUtil.publicKeyFromRawEd25519(CryptoUtil.base64UrlDecode(vector.get("client_public_key_raw_base64url").getAsString()));
        PublicKey serverPublicKey = CryptoUtil.publicKeyFromRawEd25519(CryptoUtil.base64UrlDecode(vector.get("server_public_key_raw_base64url").getAsString()));

        assertEquals(vector.get("client_public_key_sha256").getAsString(), CryptoUtil.fingerprintSha256(clientPublicKey));
        assertEquals(vector.get("server_public_key_sha256").getAsString(), CryptoUtil.fingerprintSha256(serverPublicKey));

        String clientTranscript = AuthTranscript.clientProof(new AuthTranscript.ClientProof(
            1,
            1,
            1,
            "astrbot-to-standalone",
            Role.ASTRBOT,
            "astrbot-main",
            Role.STANDALONE,
            "survival",
            "astrbot-main",
            "server-main",
            vector.get("client_public_key_sha256").getAsString(),
            "",
            "client-nonce-vector",
            "server-nonce-vector",
            "01HRCHALLENGEID000000000",
            1710000030100L,
            "01HRHELLO0000000000000000",
            "01HRCHALLENGE000000000000",
            1710000000000L,
            1710000000200L,
            vector.get("client_capabilities_sha256").getAsString(),
            vector.get("server_capabilities_sha256").getAsString()
        ));

        assertEquals(vector.get("client_transcript").getAsString(), clientTranscript);
        byte[] clientSignature = CryptoUtil.sign(clientPrivateKey, clientTranscript);
        assertEquals(vector.get("client_signature_base64url").getAsString(), CryptoUtil.base64Url(clientSignature));
        assertEquals(vector.get("client_signature_sha256").getAsString(), CryptoUtil.signatureSha256(clientSignature));
        assertTrue(CryptoUtil.verify(clientPublicKey, clientTranscript, clientSignature));

        String serverTranscript = AuthTranscript.serverProof(new AuthTranscript.ServerProof(
            1,
            1,
            1,
            "astrbot-to-standalone",
            Role.ASTRBOT,
            "astrbot-main",
            Role.STANDALONE,
            "survival",
            "astrbot-main",
            "server-main",
            vector.get("client_public_key_sha256").getAsString(),
            vector.get("server_public_key_sha256").getAsString(),
            "client-nonce-vector",
            "server-nonce-vector",
            "01HRCHALLENGEID000000000",
            1710000030100L,
            "01HRHELLO0000000000000000",
            "01HRCHALLENGE000000000000",
            "01HRAUTH0000000000000000",
            1710000000300L,
            vector.get("client_signature_sha256").getAsString(),
            vector.get("client_capabilities_sha256").getAsString(),
            vector.get("server_capabilities_sha256").getAsString()
        ));

        assertEquals(vector.get("server_transcript").getAsString(), serverTranscript);
        byte[] serverSignature = CryptoUtil.sign(serverPrivateKey, serverTranscript);
        assertEquals(vector.get("server_signature_base64url").getAsString(), CryptoUtil.base64Url(serverSignature));
        assertEquals(vector.get("server_signature_sha256").getAsString(), CryptoUtil.signatureSha256(serverSignature));
        assertTrue(CryptoUtil.verify(serverPublicKey, serverTranscript, serverSignature));
    }
}
