package com.codurance.ratelimiter.infrastructure;

import java.util.concurrent.CompletableFuture;

public class DistributedKeyValueStore {

    public CompletableFuture<Integer> incrementByAndExpire(
            String key,
            int delta,
            int expirationSeconds) throws Exception {
        throw new UnsupportedOperationException(
                "External distributed store implementation not provided.");
    }
    
}
