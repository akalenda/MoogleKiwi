package com.akalenda.MoogleKiwi.TryAndRetry;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * @author https://github.com/akalenda
 * @see {@link TryAndRetry}
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
    private long waitPeriodCap = Long.MAX_VALUE; // milliseconds
    private long currentDelta = 0; // milliseconds

    private boolean isPerpetual = true;
    private boolean isLogarithmicallyIncreasing = false;
    private boolean exceptionHandlerIsBlocking;

    /* ********************************** Constructors *****************************************/

    /**
     * @see {@link TryAndRetry#perpetually()} }
     */
    TryAndRetryTask() {
        isPerpetual = true;
    }

    /**
     * @see {@link TryAndRetry#withAttemptsUpTo(int)}
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
     * @param exceptionHandler - <p>In the case of the blocking exception handler, the supplied Predicate should return true or false: True, if after the exception it is decided that TryAndRetry should continue retrying; False, if it is decided that it should abort. This allows the handler to short-circuit the Try-Retry pattern if needed.</p>
     *                         <p>If the exception handler does short-circuit the loop, it will result in a {@link TryAndRetryFailuresException} being thrown.</p>
     */
    public TryAndRetryTask withBlockingExceptionHandler(Predicate<Exception> exceptionHandler) {
        Preconditions.checkNotNull(exceptionHandler);
        this.exceptionHandler = exceptionHandler;
        this.exceptionHandlerIsBlocking = true;
        return this;
    }

  /* ************************************* Execution *******************************************/

    /**
     * An asynchronous alternative to {@link #executeUntilDoneThenGet(Callable)}.
     *
     * @param lambda - A chunk of code to be run in the Try-Retry
     * @param <T>    - The type of data returned by the given lambda
     * @return <p>A {@link CompletableFuture} that will contain the return result of the given lambda once it has completed normally.</p>
     * <p>Depending on how the user invokes TryAndRetry options, it is possible for the lambda to complete exceptionally (e.g. {@link #executeUntilDoneThenGet(Callable)}} threw a {@link TryAndRetryFailuresException}), in which case it will not complete normally, and the return value will never be available. Instead, the CompletableFuture will complete exceptionally. See {@link CompletableFuture#exceptionally(Function)}.</p>
     */
    public <T> CompletableFuture<T> executeAsync(Callable<T> lambda) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeUntilDoneThenGet(lambda);
            } catch (TryAndRetryFailuresException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * A blocking alternative to {@link #executeAsync(Callable)}.
     *
     * @param lambda - A chunk of code to be run in the Try-Retry
     * @param <T>    - The type of data returned by the given lambda
     * @return - Returns the return result of the given lambda
     * @throws TryAndRetryFailuresException
     */
    public <T> T executeUntilDoneThenGet(Callable<T> lambda) throws TryAndRetryFailuresException {
        currentWaitPeriod = initialWaitPeriod;
        currentDelta = initialWaitPeriod;
        attemptsMade = 0;
        return continueUntilDoneThenGet(lambda);
    }

    /**
     * <p>An alternative to {@link #executeUntilDoneThenGet(Callable)}, where the state of the TryAndRetryTask is carried over from the execution of previous lambdas. As an example:</p>
     * <pre>{@code
     *      TryAndRetryTask tart = TryAndRetry
     *          .withAttemptsUpTo(20)
     *          .withWaitPeriod(15, TimeUnit.SECONDS)
     *          .linearlyIncreasingBy(5, TimeUnit.SECONDS);
     *      tart.executeUntilDoneThenGet(foo1);
     *      tart.continueUntilDoneThenGet(foo2);
     * }</pre>
     * <p>If {@code foo1} completes on its 5th attempt, then {@code foo2} will begin trying and retrying on the 6th attempt, with a wait period up to 40 seconds.</p>
     *
     * @param lambda - A chunk of code to be run in the Try-Retry
     * @param <T>    - The type of data returned by the given lambda
     * @return - Returns the return result of the given lambda
     * @throws TryAndRetryFailuresException
     */
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
        throw collapse(collectedExceptions.build());
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
            return exceptionHandler.test(e);
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
        currentWaitPeriod = Math.min(currentWaitPeriod, waitPeriodCap);
        Thread.sleep(currentWaitPeriod);
    }

    /**
     * Creates a single new exception in which the given list of exceptions are added as suppressed exceptions
     */
    private TryAndRetryFailuresException collapse(ImmutableList<Exception> immutableList) {
        TryAndRetryFailuresException tarfExc = new TryAndRetryFailuresException(attemptsMade);
        immutableList.forEach(tarfExc::addSuppressed);
        return tarfExc;
    }
}
