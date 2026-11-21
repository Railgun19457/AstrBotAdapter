package io.github.railgun19457.astrbotadapter.core.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

final class PairingCodeServiceTest {
    @Test
    void permitsOnlyOneConcurrentConsumer() throws Exception {
        PairingCodeService service = new PairingCodeService();
        PairingCodeService.PairingCode code = service.create("backend-1", Duration.ofMinutes(1));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(2);
        AtomicInteger successes = new AtomicInteger();

        for (int index = 0; index < 2; index++) {
            Thread thread = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    if (service.consume(code.pairingId(), "backend-1").status() == PairingCodeService.VerificationStatus.OK) {
                        successes.incrementAndGet();
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });
            thread.start();
        }

        ready.await();
        start.countDown();
        finished.await();

        assertEquals(1, successes.get());
    }

    @Test
    void doesNotExposePlaintextCodeInAdministrativeMetadata() {
        PairingCodeService service = new PairingCodeService();
        PairingCodeService.PairingCode code = service.create("astrbot-main", Duration.ofMinutes(1));

        PairingCodeService.PairingCode metadata = service.list().get(code.pairingId());

        assertNotNull(metadata);
        assertNull(metadata.code());
        assertEquals("astrbot-main", metadata.targetId());
    }

    @Test
    void rejectsWrongTargetWithoutConsumingPairing() {
        PairingCodeService service = new PairingCodeService();
        PairingCodeService.PairingCode code = service.create("astrbot-main", Duration.ofMinutes(1));

        assertEquals(PairingCodeService.VerificationStatus.TARGET_MISMATCH, service.lookup(code.pairingId(), "other").status());
        assertEquals(PairingCodeService.VerificationStatus.OK, service.consume(code.pairingId(), "astrbot-main").status());
    }

    @Test
    void limitsFailedSrpProofAttempts() {
        PairingCodeService service = new PairingCodeService();
        PairingCodeService.PairingCode code = service.create("astrbot-main", Duration.ofMinutes(1));

        for (int attempt = 0; attempt < 5; attempt++) {
            service.recordFailedAttempt(code.pairingId());
        }

        assertEquals(PairingCodeService.VerificationStatus.ATTEMPTS_EXCEEDED, service.lookup(code.pairingId(), "astrbot-main").status());
    }

    @Test
    void rejectsExpiredPairing() throws InterruptedException {
        PairingCodeService service = new PairingCodeService();
        PairingCodeService.PairingCode code = service.create("astrbot-main", Duration.ofMillis(1));

        Thread.sleep(10);

        assertEquals(PairingCodeService.VerificationStatus.EXPIRED, service.lookup(code.pairingId(), "astrbot-main").status());
    }

    @Test
    void revokesByPublicPairingId() {
        PairingCodeService service = new PairingCodeService();
        PairingCodeService.PairingCode code = service.create("astrbot-main", Duration.ofMinutes(1));

        assertFalse(service.revoke(code.code()));
        assertTrue(service.revoke(code.pairingId()));
        assertEquals(PairingCodeService.VerificationStatus.MISSING, service.lookup(code.pairingId(), "astrbot-main").status());
    }
}