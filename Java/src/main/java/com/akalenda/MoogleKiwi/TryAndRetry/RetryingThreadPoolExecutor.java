package com.akalenda.MoogleKiwi.TryAndRetry;


import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.util.concurrent.*;
import java.util.function.Predicate;

@SuppressWarnings("unused")
public class RetryingThreadPoolExecutor extends ThreadPoolExecutor {

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

  /* ******************** Constructors, factories ********************************************/

    public RetryingThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime,
                                      TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    public RetryingThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime,
                                      TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    public RetryingThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime,
                                      TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
    }

    public RetryingThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime,
                                      TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory,
                                      RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

  /* ****************************** Modifiers *********************************************/
    /**
     * This is the default, but you could use it just to be explicit if you really wanted to...
     */
    public RetryingThreadPoolExecutor perpetually () {
        isPerpetual = true;
        return this;
    }

    public RetryingThreadPoolExecutor withAttemptsUpTo (int attemptsAllowed) {
        Preconditions.checkArgument(attemptsAllowed > 0, "Expected positive number for attemptsAllowed, got " + attemptsAllowed);
        isPerpetual = false;
        this.attemptsAllowed = attemptsAllowed;
        return this;
    }

    /**
     * This is the default, but you could use it just to be explicit if you really wanted to...
     */
    public RetryingThreadPoolExecutor withNoWaitPeriod () {
        currentWaitPeriod = 0;
        return this;
    }

    /**
     * The converted values will be converted into milliseconds.
     * @see {@link TimeUnit#convert(long, TimeUnit)}
     */
    public RetryingThreadPoolExecutor withWaitPeriod (long waitPeriod, TimeUnit units) {
        Preconditions.checkArgument(waitPeriod > 0, "Expected positive number for waitPeriod, got " + attemptsAllowed);
        Preconditions.checkNotNull(units);
        this.initialWaitPeriod = TimeUnit.MILLISECONDS.convert(waitPeriod, units);
        this.currentWaitPeriod = initialWaitPeriod;
        return this;
    }

    public RetryingThreadPoolExecutor logarithmicallyIncreasing () {
        isLogarithmicallyIncreasing = true;
        return this;
    }

    public RetryingThreadPoolExecutor linearlyIncreasingBy (long waitPeriodIncrement, TimeUnit units) {
        Preconditions.checkArgument(waitPeriodIncrement >= 0, "Expected non-negative number for waitPeriodIncrement, got " + attemptsAllowed);
        this.waitPeriodIncrement = TimeUnit.MILLISECONDS.convert(waitPeriodIncrement, units);
        return this;
    }

    public RetryingThreadPoolExecutor upTo (long waitPeriodCap, TimeUnit units) {
        Preconditions.checkArgument(waitPeriodIncrement >= 0, "Expected non-negative number for waitPeriodCap, got " + attemptsAllowed);
        this.waitPeriodCap = TimeUnit.MILLISECONDS.convert(waitPeriodCap, units);
        return this;
    }

    /**
     *
     * @param exceptionHandler - Returning true/false only matters {@link #withBlockingExceptionHandler(Predicate)}
     */
    public RetryingThreadPoolExecutor withAsyncExceptionHandler (Predicate<Exception> exceptionHandler) {
        Preconditions.checkNotNull(exceptionHandler);
        this.exceptionHandler = exceptionHandler;
        this.exceptionHandlerIsBlocking = false;
        return this;
    }

    /**
     *
     * @param exceptionHandler - Should return true if it should retry again
     */
    public RetryingThreadPoolExecutor withBlockingExceptionHandler (Predicate<Exception> exceptionHandler) {
        Preconditions.checkNotNull(exceptionHandler);
        this.exceptionHandler = exceptionHandler;
        this.exceptionHandlerIsBlocking = true;
        return this;
    }

  /* ************************************* Execution *******************************************/

    /**
     * Hmm I dont think this one is ready to use
     * TODO I dont like this business of putting a null in the future when it fails. There has got to
     * be a way to have the exception bubble out.
     */
    public <T> CompletableFuture<T> executeAsync (Callable<T> lambda) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeUntilDoneThenGet(lambda);
            } catch (TryAndRetryFailuresException e) {
                return null;
            }
        });
    }

    public <T> T executeUntilDoneThenGet (Callable<T> lambda) throws TryAndRetryFailuresException {
        currentWaitPeriod = initialWaitPeriod;
        currentDelta = initialWaitPeriod;
        return continueUntilDoneThenGet(lambda);
    }

    public <T> T continueUntilDoneThenGet (Callable<T> lambda) throws TryAndRetryFailuresException {
        checkForConflictingModifiers();
        ImmutableList.Builder<Exception> collectedExceptions = new ImmutableList.Builder<>();
        boolean shouldRetry = true;
        for (; (isPerpetual || attemptsMade < attemptsAllowed) && shouldRetry; attemptsMade++) {
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

    private void checkForConflictingModifiers () {
        Preconditions.checkArgument(!(waitPeriodIncrement > 0 && isLogarithmicallyIncreasing),
                "Both linear and logarithmic increases were specified, please choose just one.");
        Preconditions.checkArgument(!(attemptsAllowed > 0 && isPerpetual),
                "Both perpetual and up-to-N-attempts were specified, please choose just one.");
    }

    private boolean handleException (Exception e) {
        if (exceptionHandler == null)
            return true;
        if (exceptionHandlerIsBlocking)
            return exceptionHandler.test(e); // TODO Make sure this is blocking
        Executors.newSingleThreadExecutor().execute(() -> exceptionHandler.test(e));
        return true;
    }

    private void maybeWaitBeforeRetry () throws InterruptedException {
        if (currentWaitPeriod <= 0)
            return;
        if (isLogarithmicallyIncreasing)
            currentWaitPeriod += (currentDelta /= 2);
        else
            currentWaitPeriod += waitPeriodIncrement;
        currentWaitPeriod = Math.max(currentWaitPeriod, waitPeriodCap);
        Thread.sleep(currentWaitPeriod);
    }

    private TryAndRetryFailuresException compositeOf (ImmutableList<Exception> immutableList) throws TryAndRetryFailuresException {
        TryAndRetryFailuresException tarfExc = new TryAndRetryFailuresException(attemptsMade);
        immutableList.forEach(tarfExc::addSuppressed);
        return tarfExc;
    }
}
