package com.akalenda.MoogleKiwi.TryAndRetry;

import java.util.concurrent.ExecutionException;

public class TryAndRetryFailuresException extends ExecutionException {

    private static final long serialVersionUID = 1L;

    public TryAndRetryFailuresException(int attemptsMade) {
        super("Procedure aborted after " + attemptsMade + " attempts.");
    }
}
