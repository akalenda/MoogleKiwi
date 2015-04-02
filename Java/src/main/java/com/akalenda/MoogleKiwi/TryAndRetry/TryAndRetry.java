package com.akalenda.MoogleKiwi.TryAndRetry;

/**
 * This is a front-end for {@link TryAndRetryTask}. The purpose of this package is to provide a convenient abstraction for my commonly used Try-catch-retry patterns. It uses a fluid builder pattern, so you can let your IDE take you through the options. Once your desired options are set, you can run the task with a {@code execute} variation, or {@code continue} if you don't want the state to reset. Example usage:
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

    public static TryAndRetryTask perpetually() {
        return new TryAndRetryTask();
    }

    public static TryAndRetryTask withAttemptsUpTo(int attemptsAllowed) {
        return new TryAndRetryTask(attemptsAllowed);
    }
}

