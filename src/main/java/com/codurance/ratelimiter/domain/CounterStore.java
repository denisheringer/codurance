package com.codurance.ratelimiter.domain;

import java.util.concurrent.CompletableFuture;

public interface CounterStore {
    CompletableFuture<Integer> incrementByAndExpire(String key, int delta, int expirationSeconds);
}
