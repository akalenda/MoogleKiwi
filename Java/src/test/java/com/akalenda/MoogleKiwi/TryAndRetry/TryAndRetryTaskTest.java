package com.akalenda.MoogleKiwi.TryAndRetry;

import com.google.common.collect.ImmutableList;
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

    /**
     * Tests to see that each wait period between attempts is half again the length of the previous wait period (give or take a millisecond).
     *
     * @throws Exception
     */
    public void testLogarithmicallyIncreasing() throws Exception {
        Box<Instant> startOfWaitPeriod = new Box<>();
        Box<Duration> durationOfPreviousWaitPeriod = new Box<>();
        try {
            TryAndRetry
                    .withAttemptsUpTo(20)
                    .withWaitPeriod(100, TimeUnit.MILLISECONDS)
                    .logarithmicallyIncreasing()
                    .executeUntilDoneThenGet(() -> {
                        if (startOfWaitPeriod.hasContents()) {
                            Duration durationOfCurrentWaitPeriod = Duration.between(startOfWaitPeriod.unbox(), Instant.now());
                            double expectedCurrentWaitPeriod = ((double) durationOfPreviousWaitPeriod.unbox().toMillis()) * 1.5;
                            boolean isCloseEnough = Math.abs((double) durationOfCurrentWaitPeriod.toMillis() - expectedCurrentWaitPeriod) < 1.0;
                            assertTrue("Old: " + durationOfPreviousWaitPeriod.unbox() + " New: " + durationOfCurrentWaitPeriod, isCloseEnough);
                            durationOfPreviousWaitPeriod.box(durationOfCurrentWaitPeriod);
                        }
                        startOfWaitPeriod.box(Instant.now());
                        throw new Exception("... so as to force a wait period ");
                    });
        } catch (TryAndRetryFailuresException ignored) {
        }
    }

    /**
     * Tests to see that the wait period does indeed increase linearly between wait periods
     *
     * @throws Exception
     */
    public void testLinearlyIncreasingBy() throws Exception {
        Box<Instant> startOfWaitPeriod = new Box<>();
        Box<Duration> durationOfPreviousWaitPeriod = new Box<>();
        ImmutableList.Builder<Duration> differencesInWaitPeriods = new ImmutableList.Builder<>();
        try {
            TryAndRetry
                    .withAttemptsUpTo(20)
                    .withNoWaitPeriod()
                    .linearlyIncreasingBy(500, TimeUnit.MILLISECONDS)
                    .executeUntilDoneThenGet(() -> {
                        if (startOfWaitPeriod.hasContents()) {
                            Duration durationOfCurrentWaitPeriod = Duration.between(startOfWaitPeriod.unbox(), Instant.now());
                            Duration differenceBetweenWaitPeriods = durationOfCurrentWaitPeriod.minus(durationOfPreviousWaitPeriod.unbox());
                            durationOfPreviousWaitPeriod.box(durationOfCurrentWaitPeriod);
                            differencesInWaitPeriods.add(differenceBetweenWaitPeriods);
                        }
                        startOfWaitPeriod.box(Instant.now());
                        throw new Exception("... so as to force a wait period ");
                    });
        } catch (TryAndRetryFailuresException ignored) {
        }
        differencesInWaitPeriods.build().forEach(differenceInWaitPeriods -> {
            long millis = differenceInWaitPeriods.toMillis();
            System.out.println(millis);
            assertTrue(499 <= millis && millis <= 501);
        });
    }

    /**
     * Tests to see that the time between attempts never exceeds the specified cap
     *
     * @throws Exception
     */
    public void testUpTo() throws Exception {
        Box<Instant> startOfWaitPeriod = new Box<>();
        Instant startTimer = Instant.now();
        try {
            TryAndRetry
                    .withAttemptsUpTo(20)
                    .withWaitPeriod(100, TimeUnit.MILLISECONDS)
                    .linearlyIncreasingBy(10, TimeUnit.SECONDS)
                    .upTo(2, TimeUnit.SECONDS)
                    .executeUntilDoneThenGet(() -> {
                        if (startOfWaitPeriod.hasContents()) {
                            long secondsBetweenAttempts = Duration.between(startOfWaitPeriod.unbox(), Instant.now()).getSeconds();
                            assertTrue("Seconds: " + secondsBetweenAttempts, secondsBetweenAttempts <= 2);
                        }
                        startOfWaitPeriod.box(Instant.now());
                        throw new Exception("... so as to force a wait period ");
                    });
        } catch (TryAndRetryFailuresException ignored) {
        }
    }


    /**
     * Simple container class, because Java lambdas still don't do closure properly... >:[
     *
     * @param <T>
     */
    private static class Box<T> {

        private T contents;

        public void box(T object) {
            this.contents = object;
        }

        public T unbox() {
            return contents;
        }

        public boolean hasContents() {
            return contents != null;
        }
    }
}