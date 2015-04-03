package com.akalenda.MoogleKiwi.TryAndRetry;

import junit.framework.TestCase;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TryAndRetryTaskTest extends TestCase {

    /**
     * Tests to see that the given exception handler completes asynchronously, e.g. without blocking the execution of the main task.
     *
     * @throws Exception
     */
    public void testWithAsyncExceptionHandler() throws Exception {
        Instant start = Instant.now();
        AtomicBoolean exceptionHasNotBeenThrown = new AtomicBoolean(true);
        Semaphore waitForExceptionHandlerToFinish = new Semaphore(0);
        TryAndRetry
                .withAttemptsUpTo(2)
                .withAsyncExceptionHandler(exception -> {
                    try {
                        Thread.sleep(10 * 1000);
                        waitForExceptionHandlerToFinish.release();
                        return true;
                    } catch (InterruptedException e) {
                        waitForExceptionHandlerToFinish.release();
                        return false;
                    }
                })
                .executeUntilDoneThenGet(() -> {
                    if (exceptionHasNotBeenThrown.get()) {
                        exceptionHasNotBeenThrown.set(false);
                        throw new Exception("Force the exception handler to trigger");
                    }
                    return true;
                });
        long timeToFinish = Duration.between(start, Instant.now()).toMillis();
        assertTrue(timeToFinish < 5 * 1000);
        boolean exceptionHandlerFinished = waitForExceptionHandlerToFinish.tryAcquire(10 * 10000, TimeUnit.MILLISECONDS);
        assertTrue(exceptionHandlerFinished);
    }

    /**
     * This test simply checks to see whether a given exception handler will be executed when an exception is caught. It does not, however, check to see that the handler blocks execution.
     *
     * @throws Exception
     */
    public void testWithBlockingExceptionHandler() throws Exception {
        Semaphore waitForExceptionToBeHandled = new Semaphore(0);
        Boolean handlerWorked = TryAndRetry
                .withAttemptsUpTo(2)
                .withBlockingExceptionHandler(exception -> {
                    waitForExceptionToBeHandled.release();
                    return true;
                })
                .executeUntilDoneThenGet(() -> {
                    if (waitForExceptionToBeHandled.tryAcquire())
                        return true;
                    throw new Exception("Trigger the handler");
                });
        assertEquals(Boolean.TRUE, handlerWorked);
    }

    /**
     * This is a variation on the previous test. It checks to see not only that the exception handler is invoked, but that another attempt in the Try-Retry loop is not made until the exception handler finishes.
     *
     * @throws Exception
     */
    public void testWithBlockingExceptionHandler_actuallyIsBlocking() throws Exception {
        AtomicBoolean exceptionHasNotBeenThrown = new AtomicBoolean(true);
        AtomicBoolean sleepWasUninterrupted = new AtomicBoolean();
        AtomicBoolean exceptionHasUnblocked = new AtomicBoolean(false);
        boolean executionResult = TryAndRetry
                .withAttemptsUpTo(2)
                .withBlockingExceptionHandler(exception -> {
                    exceptionHasNotBeenThrown.set(false);
                    try {
                        Thread.sleep(10 * 1000);
                        sleepWasUninterrupted.set(true);
                        exceptionHasUnblocked.set(true);
                        return true;
                    } catch (InterruptedException e) {
                        sleepWasUninterrupted.set(false);
                        return false;
                    }
                })
                .executeUntilDoneThenGet(() -> {
                    if (exceptionHasNotBeenThrown.get())
                        throw new Exception("...so as to trigger the exception handler");
                    return exceptionHasUnblocked.get();
                });
        assertEquals(executionResult, true);
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
                    try {
                        Thread.sleep(timeToSleepMillis);
                        return Instant.now();
                    } catch (InterruptedException e) {
                        return null; // Results in test failure, as desired
                    }
                });
        long timeToFinish = Duration.between(start, end).toMillis();
        assertTrue(timeToFinish >= timeToSleepMillis);
    }

    public void testContinueUntilDoneThenGet() throws Exception {
        int attemptsFirstWave = 20;
        int attemptsTotal = 55;
        AtomicInteger totalAttemptsMade = new AtomicInteger(0);
        TryAndRetryTask testTask = TryAndRetry.withAttemptsUpTo(attemptsTotal);
        testTask.executeUntilDoneThenGet(() -> {
            totalAttemptsMade.incrementAndGet();
            if (totalAttemptsMade.get() < attemptsFirstWave)
                throw new Exception("...so as to retry 20 times");
            return true;
        });
        assertEquals(attemptsFirstWave, totalAttemptsMade.get());
        try {
            testTask.continueUntilDoneThenGet(() -> {
                totalAttemptsMade.incrementAndGet();
                throw new Exception("...so as to continue retrying until all attempts are used");
            });
        } catch (TryAndRetryFailuresException ignored) {
        }
        assertEquals(attemptsTotal, totalAttemptsMade.get());
    }

    /* ***************** Tests that only exist for 100% code coverage ****************************************/

    /**
     * Tests whether the wait period between attempts is near-instantaneous, e.g. is truncated to zero milliseconds
     *
     * @throws Exception
     */
    public void testWithNoWaitPeriod() throws Exception {
        AtomicBoolean exceptionHasNotBeenThrown = new AtomicBoolean(true);
        AtomicLong millisecondsOfStart = new AtomicLong(0);
        long lengthOfWaitPeriod = TryAndRetry
                .withAttemptsUpTo(2)
                .withNoWaitPeriod()
                .executeUntilDoneThenGet(() -> {
                    long millisecondsOfEnd = System.currentTimeMillis();
                    if (exceptionHasNotBeenThrown.get()) {
                        exceptionHasNotBeenThrown.set(false);
                        millisecondsOfStart.set(System.currentTimeMillis());
                        throw new Exception("...so as to go through the wait period");
                    }
                    return millisecondsOfEnd - millisecondsOfStart.get();
                });
        assertEquals(0, lengthOfWaitPeriod);
    }

    public void testLogarithmicallyIncreasing() throws Exception {
        // TODO
    }

    public void testLinearlyIncreasingBy() throws Exception {
        // TODO
    }

    public void testUpTo() throws Exception {
        // TODO
    }
}