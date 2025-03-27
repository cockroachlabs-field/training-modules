package io.cockroachdb.training.common.retry;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;

public class TransientExceptionRetryListener implements RetryListener {
    private final AtomicInteger error = new AtomicInteger();

    private final AtomicInteger success = new AtomicInteger();

    public void clear() {
        error.set(0);
        success.set(0);
    }

    public int getError() {
        return error.get();
    }

    public int getSuccess() {
        return success.get();
    }

    @Override
    public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback,
                                                 Throwable throwable) {
        error.incrementAndGet();
    }

    @Override
    public <T, E extends Throwable> void onSuccess(RetryContext context, RetryCallback<T, E> callback, T result) {
        success.incrementAndGet();
    }
}
