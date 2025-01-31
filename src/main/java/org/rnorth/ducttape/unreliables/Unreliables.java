package org.rnorth.ducttape.unreliables;

import org.jetbrains.annotations.NotNull;
import org.rnorth.ducttape.timeouts.Timeouts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.rnorth.ducttape.Preconditions.check;

/**
 * Utilities to support automatic retry of things that may fail.
 */
public abstract class Unreliables {

    private static final Logger LOGGER = LoggerFactory.getLogger(Unreliables.class);

    /**
     * Call a supplier repeatedly until it returns a result. If an exception is thrown, the call
     * will be retried repeatedly until the timeout is hit.
     *
     * @param timeout  how long to wait
     * @param timeUnit time unit for time interval
     * @param lambda   supplier lambda expression (may throw checked exceptions)
     * @param <T>      return type of the supplier
     * @return the result of the successful lambda expression call
     */
    public static <T> T retryUntilSuccess(final long timeout, @NotNull final TimeUnit timeUnit, @NotNull final Callable<T> lambda) {

        check("timeout must be greater than zero", timeout > 0);

        final int[] attempt = {0};
        final Exception[] lastException = {null};

        final AtomicBoolean doContinue = new AtomicBoolean(true);
        try {
            return Timeouts.getWithTimeout(timeout, timeUnit, () -> {
                while (doContinue.get()) {
                    try {
                        return lambda.call();
                    } catch (Exception e) {
                        // Failed
                        LOGGER.trace("Retrying lambda call on attempt {}", attempt[0]++);
                        lastException[0] = e;
                    }
                }
                return null;
            });
        } catch (org.rnorth.ducttape.TimeoutException e) {
            if (lastException[0] != null) {
                throw new org.rnorth.ducttape.TimeoutException("Timeout waiting for result with exception", lastException[0]);
            } else {
                throw new org.rnorth.ducttape.TimeoutException(e);
            }
        } finally {
            doContinue.set(false);
        }
    }

    /**
     * Call a supplier repeatedly until it returns a result. If an exception is thrown, the call
     * will be retried repeatedly until the retry limit is hit.
     *
     * @param tryLimit how many times to try calling the supplier
     * @param lambda   supplier lambda expression (may throw checked exceptions)
     * @param <T>      return type of the supplier
     * @return the result of the successful lambda expression call
     */
    public static <T> T retryUntilSuccess(final int tryLimit, @NotNull final Callable<T> lambda) {

        check("tryLimit must be greater than zero", tryLimit > 0);

        int attempt = 0;
        Exception lastException = null;

        while (attempt < tryLimit) {
            try {
                return lambda.call();
            } catch (Exception e) {
                lastException = e;
                attempt++;
            }
        }

        throw new org.rnorth.ducttape.RetryCountExceededException("Retry limit hit with exception", lastException);
    }

    /**
     * Call a callable repeatedly until it returns true. If an exception is thrown, the call
     * will be retried repeatedly until the timeout is hit.
     *
     * @param timeout  how long to wait
     * @param timeUnit time unit for time interval
     * @param lambda   supplier lambda expression
     */
    public static void retryUntilTrue(final long timeout, @NotNull final TimeUnit timeUnit, @NotNull final Callable<Boolean> lambda) {
        retryUntilSuccess(timeout, timeUnit, () -> {
            if (!lambda.call()) {
                throw new RuntimeException("Not ready yet");
            } else {
                return null;
            }
        });
    }

    /**
     * Call a callable repeatedly until it returns true. If an exception is thrown, the call
     * will be retried repeatedly until the timeout is hit.
     *
     * @param tryLimit how many times to try calling the supplier
     * @param lambda   supplier lambda expression
     */
    public static void retryUntilTrue(final int tryLimit, @NotNull final Callable<Boolean> lambda) {
        retryUntilSuccess(tryLimit, () -> {
            if (!lambda.call()) {
                throw new RuntimeException("Not ready yet");
            } else {
                return null;
            }
        });
    }
}
