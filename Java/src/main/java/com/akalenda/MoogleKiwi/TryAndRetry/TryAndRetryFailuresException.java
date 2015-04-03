package com.akalenda.MoogleKiwi.TryAndRetry;

import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

/**
 * This exception occurs when TryAndRetry finishes making attempts without producing a result.
 *
 * @see {@link TryAndRetry#withAttemptsUpTo(int)}
 * @see {@link TryAndRetryTask#withBlockingExceptionHandler(Predicate)}
 */
public class TryAndRetryFailuresException extends ExecutionException {

    private static final long serialVersionUID = 1L;

    public TryAndRetryFailuresException(int attemptsMade) {
        super("Procedure aborted after " + attemptsMade + " attempts.");
    }
}
