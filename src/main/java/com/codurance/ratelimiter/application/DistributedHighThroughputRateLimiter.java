package com.codurance.ratelimiter.application;

import com.codurance.ratelimiter.domain.CounterStore;
import com.codurance.ratelimiter.domain.RateLimiter;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DistributedHighThroughputRateLimiter implements RateLimiter {

    private final CounterStore distributedCounterStore;
    private final RateLimiterConfig rateLimiterConfig;
    private final Clock clock;

    private final Map<String, KeyState> keyStateByKey = new ConcurrentHashMap<>();

    public DistributedHighThroughputRateLimiter(CounterStore distributedCounterStore,
                                                RateLimiterConfig rateLimiterConfig,
                                                ScheduledExecutorService scheduler,
                                                Clock clock) {
        this.distributedCounterStore = distributedCounterStore;
        this.rateLimiterConfig = rateLimiterConfig;
        this.clock = clock;

        scheduler.scheduleAtFixedRate(
                this::flushAllKeys,
                rateLimiterConfig.getFlushIntervalMillis(),
                rateLimiterConfig.getFlushIntervalMillis(),
                TimeUnit.MILLISECONDS
        );

        scheduler.scheduleAtFixedRate(
                this::removeInactiveKeys,
                rateLimiterConfig.getFlushIntervalMillis(),
                rateLimiterConfig.getFlushIntervalMillis(),
                TimeUnit.MILLISECONDS
        );
    }

    @Override
    public CompletableFuture<Boolean> isAllowed(String key, int limit) {
        long currentTimestamp = clock.millis();
        long currentWindowStartTimestamp = computeWindowStartTimestamp(currentTimestamp);

        KeyState keyState = keyStateByKey.computeIfAbsent(
                key,
                unusedKey -> new KeyState(currentWindowStartTimestamp, currentTimestamp)
        );

        keyState.updateLastAccessTimestamp(currentTimestamp);

        if (!keyState.isSameWindow(currentWindowStartTimestamp)) {
            keyState.resetWindow(currentWindowStartTimestamp);
        }

        int updatedLocalRequestCount = keyState.incrementPendingRequestCount();

        int estimatedTotalRequestCount = keyState.getLastKnownGlobalRequestCount() + updatedLocalRequestCount;

        boolean allowed = estimatedTotalRequestCount <= limit;

        if (keyState.getPendingRequestCount() >= rateLimiterConfig.getLocalCountFlushThreshold()) {
            flushKey(key, keyState);
        }

        return CompletableFuture.completedFuture(allowed);
    }

    private void flushKey(String key, KeyState keyState) {
        if (!keyState.tryStartFlush()) {
            return;
        }

        int drainedRequestCount = keyState.drainPendingRequestCount();

        long flushWindowStartTimestamp = keyState.getWindowStartTimestamp();

        distributedCounterStore.incrementByAndExpire(
                        key,
                        drainedRequestCount,
                        rateLimiterConfig.getWindowDurationSeconds()
                )
                .whenComplete((updatedGlobalRequestCount, throwable) -> {
                    try {
                        if (keyState.isSameWindow(flushWindowStartTimestamp)) {
                            if (throwable != null) {
                                keyState.restorePendingRequestCount(drainedRequestCount);
                            } else {
                                keyState.updateLastKnownGlobalRequestCount(updatedGlobalRequestCount);
                            }
                        }
                    } finally {
                        keyState.finishFlush();
                    }
                });
    }

    void flushAllKeys() {
        keyStateByKey.forEach(this::flushKey);
    }

    void removeInactiveKeys() {
        long currentTimestamp = clock.millis();
        long evictionCutoffTimestamp =
                currentTimestamp - TimeUnit.SECONDS.toMillis(
                    rateLimiterConfig.getWindowDurationSeconds());

        keyStateByKey.entrySet().removeIf(entry ->
            entry.getValue().getLastAccessTimestamp() < evictionCutoffTimestamp
        );
    }

    boolean hasKey(String key) {
        return keyStateByKey.containsKey(key);
    }

    private long computeWindowStartTimestamp(long currentTimestamp) {
        long windowDurationMillis =
                TimeUnit.SECONDS.toMillis(
                    rateLimiterConfig.getWindowDurationSeconds());

        return currentTimestamp - (currentTimestamp % windowDurationMillis);
    }

}