package com.codurance.ratelimiter.application;

public class RateLimiterConfig {

    private final int windowDurationSeconds;
    private final int localCountFlushThreshold;
    private final int flushIntervalMillis;

    public RateLimiterConfig(int windowDurationSeconds,
                             int localCountFlushThreshold,
                             int flushIntervalMillis) {
        this.windowDurationSeconds = windowDurationSeconds;
        this.localCountFlushThreshold = localCountFlushThreshold;
        this.flushIntervalMillis = flushIntervalMillis;
    }

    public int getWindowDurationSeconds() {
        return windowDurationSeconds;
    }

    public int getLocalCountFlushThreshold() {
        return localCountFlushThreshold;
    }

    public int getFlushIntervalMillis() {
        return flushIntervalMillis;
    }

}
