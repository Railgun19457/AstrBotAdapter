package io.github.railgun19457.astrbotadapter.transport;

import io.github.railgun19457.astrbotadapter.protocol.SecureChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrustStoreTest {
    @Test
    void consumesPairingAndRetainsTrustAndRevocationAcrossRestarts(@TempDir Path directory) throws Exception {
        var store = new TrustStore(directory);
        var first = store.create("astrbot");
        var current = store.create("astrbot");
        String clientKey = SecureChannel.base64(SecureChannel.keyPair("Ed25519").getPublic().getEncoded());

        assertNull(store.find(first.id(), "astrbot"));
        assertNull(store.find(current.id(), "another-client"));
        store.enroll(current, clientKey);
        assertThrows(IllegalStateException.class, () -> store.enroll(current, clientKey));
        assertThrows(IllegalArgumentException.class, () -> store.create("astrbot"));

        var reloaded = new TrustStore(directory);
        assertEquals(store.identity().getPublic(), reloaded.identity().getPublic());
        assertTrue(reloaded.trusted("astrbot", clientKey));
        assertTrue(reloaded.revoke("astrbot"));
        assertFalse(new TrustStore(directory).trusted("astrbot", clientKey));
    }
}
