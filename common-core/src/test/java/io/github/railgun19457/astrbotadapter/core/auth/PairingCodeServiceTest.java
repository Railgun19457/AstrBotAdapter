package io.github.railgun19457.astrbotadapter.core.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
                    if (service.verifyAndUseDetailed(code.code(), "backend-1").status() == PairingCodeService.VerificationStatus.OK) {
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
}