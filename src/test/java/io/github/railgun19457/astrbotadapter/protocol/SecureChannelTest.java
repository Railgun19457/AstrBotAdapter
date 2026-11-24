package io.github.railgun19457.astrbotadapter.protocol;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecureChannelTest {
    @Test
    void authenticatesBothDirectionsAndRejectsReplay() throws Exception {
        var clientKey = SecureChannel.keyPair("X25519");
        var serverKey = SecureChannel.keyPair("X25519");
        byte[] transcript = SecureChannel.transcript("test", "client", "server");
        byte[] pairing = new byte[24];
        var client = SecureChannel.derive(clientKey.getPrivate(),
            SecureChannel.base64(serverKey.getPublic().getEncoded()), transcript, pairing, false);
        var server = SecureChannel.derive(serverKey.getPrivate(),
            SecureChannel.base64(clientKey.getPublic().getEncoded()), transcript, pairing, true);
        JsonObject message = new JsonObject();
        message.addProperty("text", "Minecraft 测试");

        JsonObject frame = JsonParser.parseString(client.encode(message)).getAsJsonObject();
        JsonObject modified = frame.deepCopy();
        byte[] ciphertext = SecureChannel.bytes(modified.get("ciphertext").getAsString());
        ciphertext[0] ^= 1;
        modified.addProperty("ciphertext", SecureChannel.base64(ciphertext));
        assertThrows(GeneralSecurityException.class, () -> server.decode(modified));
        assertEquals(message, server.decode(frame));
        assertThrows(GeneralSecurityException.class, () -> server.decode(frame));
        assertEquals(message, client.decode(JsonParser.parseString(server.encode(message)).getAsJsonObject()));
    }
}
