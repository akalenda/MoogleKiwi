package com.akalenda.MoogleKiwi.TryAndRetry;

import java.util.concurrent.Callable;
import java.util.function.Predicate;

/**
 * <p>This is a front-end for {@link TryAndRetryTask}. The purpose of this package is to provide a convenient abstraction for my commonly used Try-catch-retry patterns. It uses a fluid builder pattern, so you can let your IDE take you through the options. First choose whether or not to bound the loop to a particular number of attempts. Then choose zero or more options. Finally, execute your task by providing one of the following:</p>
 * <ul>
 *     <li>{@link TryAndRetryTask#executeUntilDoneThenGet(Callable)} </li>
 *     <li>{@link TryAndRetryTask#continueUntilDoneThenGet(Callable)} </li>
 *     <li>{@link TryAndRetryTask#executeAsync(Callable)} </li>
 * </ul>
 * <p>Example usage:</p>
 * <pre> {@code
 * myConnection = TryAndRetry
 *      .withAttemptsUpTo(5)
 *      .withWaitPeriod(1, TimeUnit.MINUTES)
 *      .linearlyIncreasingBy(30, TimeUnit.SECONDS)
 *      .upTo(10, TimeUnit.MINUTES)
 *      .withAsyncExceptionHandler(exception -> logger.catching(exception))
 *      .executeUntilDoneThenGet(() -> getConnectionToRemote());
 * }</pre>}
 *
 * TODO look at sTrace
 *
 * @author https://github.com/akalenda
 */
@SuppressWarnings("unused")
public class TryAndRetry {

    /**
     * Creates a task that will run perpetually until it is successful. (Or, see {@link TryAndRetryTask#withBlockingExceptionHandler(Predicate)}.)
     * @return - A TryAndRetryTask, which can be used to execute one or more {@link Callable}s
     */
    public static TryAndRetryTask perpetually() {
        return new TryAndRetryTask();
    }

    /**
     * <p>Factory method which creates a task that will run perpetually until it is successful, or it has tried and retried as many times as is allowed. (Or, see {@link TryAndRetryTask#withBlockingExceptionHandler(Predicate)}.)</p>
     * <p>If a task fails to complete normally this number of attempts, a {@link TryAndRetryFailuresException will be thrown.</p>
     *
     * @param attemptsAllowed - Must be a positive number; this is how many times it will try and retry before aborting.
     * @return - A TryAndRetryTask, which can be used to execute one or more {@link Callable}s
     */
    public static TryAndRetryTask withAttemptsUpTo(int attemptsAllowed) {
        return new TryAndRetryTask(attemptsAllowed);
    }
}

