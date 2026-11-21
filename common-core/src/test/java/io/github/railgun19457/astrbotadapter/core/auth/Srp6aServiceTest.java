package io.github.railgun19457.astrbotadapter.core.auth;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Srp6aServiceTest {
    @Test
    void completesMutualSrpProofForOneTimeCode() throws Exception {
        String pairingId = "PAIRING-TEST";
        String identity = Srp6aService.pairingIdentity(pairingId);
        Srp6aService.Registration registration = Srp6aService.register(identity, "test-code-with-sufficient-entropy");
        Srp6aService.ServerSession server = Srp6aService.startServer(registration);
        Srp6aService.ClientSession client = Srp6aService.createClientProof(
            identity,
            "test-code-with-sufficient-entropy",
            registration.salt(),
            server.serverPublicValue()
        );

        Srp6aService.ServerProofResult proofResult = Srp6aService.verifyClientProof(server, client.clientPublicValue(), identity, client.clientProof());

        assertNotNull(proofResult);
        assertTrue(Srp6aService.verifyServerProof(client, proofResult.serverProof()));
        String binding = Srp6aService.keyBindingConfirmation(
            proofResult.sessionKey(),
            pairingId,
            "astrbot",
            "astrbot-main",
            "standalone",
            "survival",
            "astrbot-main",
            "client-public-key",
            "server-main",
            "server-public-key",
            registration.salt(),
            client.clientPublicValue(),
            server.serverPublicValue(),
            client.clientProof(),
            proofResult.serverProof()
        );
        String clientBinding = Srp6aService.keyBindingConfirmation(
            client.sessionKey(),
            pairingId,
            "astrbot",
            "astrbot-main",
            "standalone",
            "survival",
            "astrbot-main",
            "client-public-key",
            "server-main",
            "server-public-key",
            registration.salt(),
            client.clientPublicValue(),
            server.serverPublicValue(),
            client.clientProof(),
            proofResult.serverProof()
        );
        assertTrue(binding.equals(clientBinding));
    }

    @Test
    void rejectsProofDerivedFromWrongPairingCode() throws Exception {
        String identity = Srp6aService.pairingIdentity("PAIRING-TEST");
        Srp6aService.Registration registration = Srp6aService.register(identity, "correct-code");
        Srp6aService.ServerSession server = Srp6aService.startServer(registration);
        Srp6aService.ClientSession client = Srp6aService.createClientProof(
            identity,
            "wrong-code",
            registration.salt(),
            server.serverPublicValue()
        );

        assertNull(Srp6aService.verifyClientProof(server, client.clientPublicValue(), identity, client.clientProof()));
    }

    @Test
    void acceptsFixedPysrpRfc5054Vector() throws Exception {
        Srp6aService.Registration registration = new Srp6aService.Registration(
            "ABEiM0RVZneImaq7zN3u_wARIjNEVWZ3iJmqu8zd7v8",
            "C81v1Insvh0s-nvJLB9aFwkw63TQACr7nN_f0CXHHs5LY6CW66ESZeLrjnY1E0c1_Nz0g4c6z6hms-sQvg9CUV0i_jujZZEcZJbZX__swqrrT3zIRfiJU7Tgw0LQqK6aCYyBAyasrIZ8EIicbNyERDt2XJhppatDGY_0re2j64LYIqE5hSNxei-YMkBdLextdHVx8OeNCcBYmvymQxUS8Vh8rV3Sj-QefVzTCy7D1UHbsoPfBAWqSsyoAnBJ0jFxDVTqK1WdV0Ag5gy8KO0CPw9MEU3I6cmw7zZuMEKqRiQRU_kGjqbiCSBlaBGuQ_u6UhKCANIes1qYBi8IgI1n1A"
        );
        byte[] serverPrivateValue = new byte[256];
        serverPrivateValue[0] = (byte) 0x80;
        java.util.Arrays.fill(serverPrivateValue, 1, serverPrivateValue.length, (byte) 0x01);
        Srp6aService.ServerSession server = Srp6aService.startServer(registration, new FixedSecureRandom(serverPrivateValue));

        assertEquals(
            "B1AXw3rt2CfzLkqaIE9LxBEdGMDgbmVPXrOMDk-BoFrnvFdKCNH1UiS83ngUOmq3pmQs_6drpiGTttZ7vnPsXP2ZyuuWTHg9LMelcvknKAHp4Wja4mo7JVPTcyxtCbGXoJwYAU4wCRWcC2787rGxOirBicUfBN-bbRu4rvpgoTqYDQn_IRDpEOikIsukFrUPohyTwcv1TwYw9YCRByWUZEC4Ts6f4rB-ohPtyl2AiRcepPaSCVhqvVqvv1A6KPeVNnWWx7g-eg8tQOUUMv4YO6JqF5p5oOTJmQp_-q7d6MrU6YUYkGvRhf33kxcuJN6gPV-FikE6c6eeKkW9xmvHiw",
            server.serverPublicValue()
        );
        Srp6aService.ServerProofResult result = Srp6aService.verifyClientProof(
            server,
            "CQK9CmdTUgJBr_YHyDEPvgktCndvj6Fan1lzTuqO6-jeyLB8qxoFQcgydmQk3ZxiDOx9ft1Nd3vqNV28z__-bA-xtFG-gs77Ucuowb6dQu1dRPNhNQplOzNT5LJxEKX0TEoeCRTc42k0cAbcY_Nfsn9ryWHlDRGP0rXtVEjx_603D_C7gctYnsDrB9_vwGwzET1KaZDw1OKLaOPy63B9ejZy6cheKJFC2BewSD-3era8X864emLARs9gcTrkBfD_WLwpP7wE2wLEU_DGGC8D94gZf098Sgb30hiies4tcSEGjB0nj7nOkEY7g4hKp1j1s84BfGC2_C3KoUUzF0Ieuw",
            "astrbot-minecraft-pairing-v1:PYTHON-VECTOR",
            "X5CgAhihTFnEuIDzzgS1ucceij1p4QmDcAjS6JZeXwg"
        );

        assertNotNull(result);
        assertEquals("577-RanrccdPBUuzwC4PFdmAR_tRaXNXSoHUEtETz0U", result.serverProof());
    }

    private static final class FixedSecureRandom extends SecureRandom {
        private final byte[] value;

        private FixedSecureRandom(byte[] value) {
            this.value = value.clone();
        }

        @Override
        public void nextBytes(byte[] bytes) {
            if (bytes.length != value.length) {
                throw new IllegalArgumentException("unexpected random byte length");
            }
            System.arraycopy(value, 0, bytes, 0, bytes.length);
        }
    }
}