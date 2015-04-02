package com.akalenda.MoogleKiwi.TryAndRetry;

import junit.framework.TestCase;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TryAndRetryTaskTest extends TestCase {

    private final Semaphore semaphore = new Semaphore(1);

    // TODO: These need to have their behavior monitored inside the debugger with breakpoints, I might have bungled the tests after all...
    public void testWithAsyncExceptionHandler() throws Exception {
        Instant start = Instant.now();
        Semaphore failOnce = new Semaphore(1);
        TryAndRetry
                .withAttemptsUpTo(2)
                .withAsyncExceptionHandler(exception -> {
                    try {
                        Thread.sleep(1000 * 1000);
                    } catch (InterruptedException ignored) {}
                    return true;
                })
                .executeUntilDoneThenGet(() -> {
                    if (failOnce.tryAcquire())
                        throw new Exception("Force the exception handler to trigger");
                    return true;
                });
        long timeToFinish = Duration.between(start, Instant.now()).toMillis();
        assertTrue(timeToFinish < 500 * 1000);
    }

    // TODO: This doesn't test to see that the exception handler actually is blocking execution. e.g. The TryAndRetry loop should be waiting for the handler to finish before moving on to the next attempt. This doesn't explicitly test that; rather, it's just testing to see that the exception occurs at all.
    public void testWithBlockingExceptionHandler() throws Exception {
        Semaphore waitForExceptionToBeHandled = new Semaphore(0);
        Boolean handlerWorked = TryAndRetry
                .withAttemptsUpTo(1)
                .withBlockingExceptionHandler(exception -> {
                    waitForExceptionToBeHandled.release();
                    return true;
                })
                .executeUntilDoneThenGet(() -> {
                    if (semaphore.tryAcquire())
                        return true;
                    throw new Exception("Trigger the handler");
                });
        assertEquals(Boolean.TRUE, handlerWorked);
    }

    public void testWithBlockingExceptionHandler_abort() throws Exception {
        boolean handlerAbortWorked;
        try {
            handlerAbortWorked = TryAndRetry
                    .withAttemptsUpTo(1)
                    .withBlockingExceptionHandler(exception -> false)
                    .executeUntilDoneThenGet(() -> {
                        throw new Exception("Force failure and trigger exception");
                    });
        } catch (TryAndRetryFailuresException e) {
            handlerAbortWorked = true;
        }
        assertTrue(handlerAbortWorked);
    }

    public void testExecuteAsync() throws Exception {
        Semaphore lambdaWaitsForMainThreadToRelease = new Semaphore(0);
        CompletableFuture<Boolean> asynchWorked = TryAndRetry
                .withAttemptsUpTo(20)
                .withWaitPeriod(1, TimeUnit.SECONDS)
                .executeAsync(() -> {
                    if (lambdaWaitsForMainThreadToRelease.tryAcquire())
                        return true;
                    throw new Exception("...so as to force retries");
                });
        lambdaWaitsForMainThreadToRelease.release();
        assertTrue(asynchWorked.get(25, TimeUnit.SECONDS));
    }

    public void testExecuteUntilDoneThenGet() throws Exception {
        Instant start = Instant.now();
        long timeToSleepMillis = 2 * 1000;
        Instant end = TryAndRetry
            .withAttemptsUpTo(1)
            .executeUntilDoneThenGet(() -> {
                Thread.sleep(timeToSleepMillis);
                return Instant.now();
            });
        long timeToFinish = Duration.between(start, end).toMillis();
        assertTrue (timeToFinish >= timeToSleepMillis);
    }

    /*
        TODO: This doesnt pass yet. Off-by-one error. I think it's because the successful attempt on executeUntilDoneThenGet does not increment the number of attempts made.
      */
    public void testContinueUntilDoneThenGet() throws Exception {
        int attemptsPerWave = 55;
        AtomicInteger totalAttemptsMade = new AtomicInteger(0);
        TryAndRetryTask testTask = TryAndRetry.withAttemptsUpTo(attemptsPerWave * 2);
        testTask.executeUntilDoneThenGet(() -> {
            totalAttemptsMade.incrementAndGet();
            if (totalAttemptsMade.get() < 20)
                throw new Exception("...so as to retry 20 times");
            return true;
        });
        try {
            testTask.continueUntilDoneThenGet(() -> {
                totalAttemptsMade.incrementAndGet();
                throw new Exception("...so as to continue retrying until all attempts are used");
            });
        } catch (TryAndRetryFailuresException ignored) {}
        assertEquals(attemptsPerWave * 2, totalAttemptsMade.get());
    }
}