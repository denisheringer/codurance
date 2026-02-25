package com.codurance.ratelimiter.domain;

import java.util.concurrent.CompletableFuture;

public interface RateLimiter {
    CompletableFuture<Boolean> isAllowed(String key, int limit);
}
