package com.akalenda.MoogleKiwi.TryAndRetry;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;


/**
 * Example usage:
 * <pre>
 * myConnection = TryAndRetry.
 *      withAttemptsUpTo(5).
 *      withWaitPeriod(1, TimeUnit.MINUTES).
 *      linearlyIncreasingBy(30, TimeUnit.SECONDS).
 *      upTo(10, TimeUnit.MINUTES).
 *      withAsyncExceptionHandler(exception -> logger.catching(exception)).
 *      executeUntilDoneThenGet(() -> getConnectionToRemote());
 * </pre>
 *
 * TODO look at sTrace
 *
 * @author https://github.com/akalenda
 */
@SuppressWarnings("unused")
public class TryAndRetry {

    private static RetryingThreadPoolExecutor factory() {
        return new RetryingThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS,
                new SynchronousQueue<>());
    }

    public static RetryingThreadPoolExecutor perpetually() {
        return factory().perpetually();
    }

    public static RetryingThreadPoolExecutor withAttemptsUpTo(int attemptsAllowed) {
        return factory().withAttemptsUpTo(attemptsAllowed);
    }

    public static void main (String[] args) {
        System.out.println("Hello world");
    }
}

