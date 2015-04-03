package com.akalenda.MoogleKiwi.TryAndRetry;

import junit.framework.TestCase;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by akalenda on 4/2/2015.
 */
public class TryAndRetryTest extends TestCase {

    /**
     * Unfortunately the test needs to end at some point, we can't really test whether it runs perpetually. But that's not a big deal, we can assume that if it retries a few times, it will retry however many times.
     * @throws Exception
     */
    public void testPerpetually() throws Exception {
        AtomicInteger timesWeForcedAnException = new AtomicInteger(0);
        boolean retriedUntilCompleted = TryAndRetry
                .perpetually()
                .executeUntilDoneThenGet(() -> {
                    if (timesWeForcedAnException.get() < 10) {
                        timesWeForcedAnException.incrementAndGet();
                        throw new Exception("...so as to force it to retry");
                    }
                    return true;
                });
        assertTrue(retriedUntilCompleted);
        assertEquals(10, timesWeForcedAnException.get());
    }

    public void testWithAttemptsUpTo() throws Exception {
        int attemptsToMake = 55;
        AtomicInteger attemptsMade = new AtomicInteger(0);
        try {
            TryAndRetry
                .withAttemptsUpTo(attemptsToMake)
                .executeUntilDoneThenGet(() -> {
                    attemptsMade.incrementAndGet();
                    throw new Exception("...so as to continue retrying until all attempts are used");
                });
        } catch (TryAndRetryFailuresException ignored) {}
        assertEquals(attemptsToMake, attemptsMade.get());
    }
}