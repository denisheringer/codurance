package com.codurance.ratelimiter.testutil;

import com.codurance.ratelimiter.domain.CounterStore;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeCounterStore implements CounterStore {

    private final AtomicInteger requestCount = new AtomicInteger(0);
    private volatile boolean fail = false;

    public void setFail(boolean fail) {
        this.fail = fail;
    }

    public int getRequestCount() {
        return requestCount.get();
    }

    @Override
    public CompletableFuture<Integer> incrementByAndExpire(
            String key,
            int delta,
            int expirationSeconds) {

        if (fail) {
            CompletableFuture<Integer> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("store failure"));
            return future;
        }

        int updated = requestCount.addAndGet(delta);

        return CompletableFuture.completedFuture(updated);
    }
}
