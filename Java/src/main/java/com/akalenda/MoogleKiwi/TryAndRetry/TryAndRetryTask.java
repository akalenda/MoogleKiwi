package com.akalenda.MoogleKiwi.TryAndRetry;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.util.concurrent.*;
import java.util.function.Predicate;

/**
 * @author https://github.com/akalenda
 */
@SuppressWarnings("unused")
public class TryAndRetryTask {

    /* ************************ Fields ********************************************************/
    private Predicate<Exception> exceptionHandler = null;

    private int attemptsMade = 0;
    private int attemptsAllowed = 0;
    private long initialWaitPeriod = 0; // milliseconds
    private long currentWaitPeriod = 0; // milliseconds
    private long waitPeriodIncrement = 0; // milliseconds
    private long waitPeriodCap = -1; // milliseconds
    private long currentDelta; // milliseconds

    private boolean isPerpetual = true;
    private boolean isLogarithmicallyIncreasing = false;
    private boolean exceptionHandlerIsBlocking;

    /* ********************************** Constructors *****************************************/

    /**
     * Creates a task that will run perpetually until it is successful. (Or, see {@link #withBlockingExceptionHandler(Predicate)}.)
     */
    TryAndRetryTask() {
        isPerpetual = true;
    }

    /**
     * Creates a task that will run perpetually until it is successful, or it has tried and retried as many times as is allowed. (Or, see {@link #withBlockingExceptionHandler(Predicate)}.)
     *
     * @param attemptsAllowed - Must be a positive number; this is how many times it will try and retry before aborting.
     */
    TryAndRetryTask(int attemptsAllowed) {
        Preconditions.checkArgument(attemptsAllowed > 0, "Expected positive number for attemptsAllowed, got " + attemptsAllowed);
        this.attemptsAllowed = attemptsAllowed;
        isPerpetual = false;
    }

  /* ****************************** Modifiers *********************************************/

    /**
     * This is the default, but you could use it just to be explicit if you really wanted to...
     */
    public TryAndRetryTask withNoWaitPeriod() {
        currentWaitPeriod = 0;
        return this;
    }

    /**
     * The values will be converted into milliseconds.
     *
     * @see {@link TimeUnit#convert(long, TimeUnit)}
     */
    public TryAndRetryTask withWaitPeriod(long waitPeriod, TimeUnit units) {
        Preconditions.checkArgument(waitPeriod > 0, "Expected positive number for waitPeriod, got " + attemptsAllowed);
        Preconditions.checkNotNull(units);
        this.initialWaitPeriod = TimeUnit.MILLISECONDS.convert(waitPeriod, units);
        this.currentWaitPeriod = initialWaitPeriod;
        return this;
    }

    /**
     * With each successive retry, the wait period will increase by half of what it increased before.
     */
    public TryAndRetryTask logarithmicallyIncreasing() {
        isLogarithmicallyIncreasing = true;
        return this;
    }

    /**
     * @param waitPeriodIncrement - How many units by which to increase the wait period between retries
     * @param units               - What type of units to use; will be converted into TimeUnit.MILLISECONDS
     * @see {@link TimeUnit#convert(long, TimeUnit)}
     */
    public TryAndRetryTask linearlyIncreasingBy(long waitPeriodIncrement, TimeUnit units) {
        Preconditions.checkArgument(waitPeriodIncrement >= 0, "Expected non-negative number for waitPeriodIncrement, got " + attemptsAllowed);
        this.waitPeriodIncrement = TimeUnit.MILLISECONDS.convert(waitPeriodIncrement, units);
        return this;
    }

    /**
     * @param waitPeriodCap - The maximum wait period allowable
     * @param units         - What type of units to use; will be converted into TimeUnit.MILLISECONDS
     * @see {@link TimeUnit#convert(long, TimeUnit)}
     */
    public TryAndRetryTask upTo(long waitPeriodCap, TimeUnit units) {
        Preconditions.checkArgument(waitPeriodIncrement >= 0, "Expected non-negative number for waitPeriodCap, got " + attemptsAllowed);
        this.waitPeriodCap = TimeUnit.MILLISECONDS.convert(waitPeriodCap, units);
        return this;
    }

    /**
     * @param exceptionHandler - A {@link Predicate} (lambda or method reference expected) that accepts an {@link Exception} as its argument, and deals with it as the application requires. The return value of that Predicate is discarded. (The return value, by contrast, <i>does</i> matter with the alternative method {@link #withBlockingExceptionHandler(Predicate)}).
     */
    public TryAndRetryTask withAsyncExceptionHandler(Predicate<Exception> exceptionHandler) {
        Preconditions.checkNotNull(exceptionHandler);
        this.exceptionHandler = exceptionHandler;
        this.exceptionHandlerIsBlocking = false;
        return this;
    }

    /**
     * @param exceptionHandler - In the case of the blocking exception handler, the supplied Predicate should return true or false: True, if after the exception it is decided that TryAndRetry should continue retrying; False, if it is decided that it should abort. This allows the handler to short-circuit the Try-Retry pattern if needed.
     */
    public TryAndRetryTask withBlockingExceptionHandler(Predicate<Exception> exceptionHandler) {
        Preconditions.checkNotNull(exceptionHandler);
        this.exceptionHandler = exceptionHandler;
        this.exceptionHandlerIsBlocking = true;
        return this;
    }

  /* ************************************* Execution *******************************************/

    public <T> CompletableFuture<T> executeAsync(Callable<T> lambda) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeUntilDoneThenGet(lambda);
            } catch (TryAndRetryFailuresException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public <T> T executeUntilDoneThenGet(Callable<T> lambda) throws TryAndRetryFailuresException {
        currentWaitPeriod = initialWaitPeriod;
        currentDelta = initialWaitPeriod;
        return continueUntilDoneThenGet(lambda);
    }

    public <T> T continueUntilDoneThenGet(Callable<T> lambda) throws TryAndRetryFailuresException {
        checkForConflictingModifiers();
        ImmutableList.Builder<Exception> collectedExceptions = new ImmutableList.Builder<>();
        boolean shouldRetry = true;
        while ((isPerpetual || attemptsMade < attemptsAllowed) && shouldRetry) {
            attemptsMade++;
            try {
                return lambda.call();
            } catch (Exception e) {
                collectedExceptions.add(e);
                shouldRetry = handleException(e);
                try {
                    maybeWaitBeforeRetry();
                } catch (InterruptedException e1) {
                    collectedExceptions.add(e1);
                }
            }
        }
        throw compositeOf(collectedExceptions.build());
    }

  /* ************************************** Helpers *******************************************/

    private void checkForConflictingModifiers() {
        Preconditions.checkArgument(!(waitPeriodIncrement > 0 && isLogarithmicallyIncreasing),
                "Both linear and logarithmic increases were specified, please choose just one.");
        Preconditions.checkArgument(!(attemptsAllowed > 0 && isPerpetual),
                "Both perpetual and up-to-N-attempts were specified, please choose just one.");
    }

    private boolean handleException(Exception e) {
        if (exceptionHandler == null)
            return true;
        if (exceptionHandlerIsBlocking)
            return exceptionHandler.test(e); // TODO Make sure this is blocking
        Executors.newSingleThreadExecutor().execute(() -> exceptionHandler.test(e));
        return true;
    }

    private void maybeWaitBeforeRetry() throws InterruptedException {
        if (currentWaitPeriod <= 0)
            return;
        if (isLogarithmicallyIncreasing)
            currentWaitPeriod += (currentDelta /= 2);
        else
            currentWaitPeriod += waitPeriodIncrement;
        currentWaitPeriod = Math.max(currentWaitPeriod, waitPeriodCap);
        Thread.sleep(currentWaitPeriod);
    }

    private TryAndRetryFailuresException compositeOf(ImmutableList<Exception> immutableList) throws TryAndRetryFailuresException {
        TryAndRetryFailuresException tarfExc = new TryAndRetryFailuresException(attemptsMade);
        immutableList.forEach(tarfExc::addSuppressed);
        return tarfExc;
    }
}
