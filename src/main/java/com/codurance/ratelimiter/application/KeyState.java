package com.codurance.ratelimiter.application;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class KeyState {

    private volatile long windowStartTimestamp;
    private volatile long lastAccessTimestamp;
    private volatile int lastKnownGlobalRequestCount = 0;

    private final AtomicInteger pendingRequestCount = new AtomicInteger(0);
    private final AtomicBoolean flushInProgress = new AtomicBoolean(false);

    KeyState(long initialWindowStartTimestamp, long currentTimestamp) {
        this.windowStartTimestamp = initialWindowStartTimestamp;
        this.lastAccessTimestamp = currentTimestamp;
    }

    void resetWindow(long newWindowStartTimestamp) {
        this.windowStartTimestamp = newWindowStartTimestamp;
        this.pendingRequestCount.set(0);
        this.lastKnownGlobalRequestCount = 0;
    }

    long getWindowStartTimestamp() {
        return windowStartTimestamp;
    }

    void updateLastAccessTimestamp(long currentTimestamp) {
        this.lastAccessTimestamp = currentTimestamp;
    }

    long getLastAccessTimestamp() {
        return lastAccessTimestamp;
    }

    int incrementPendingRequestCount() {
        return pendingRequestCount.incrementAndGet();
    }

    int getPendingRequestCount() {
        return pendingRequestCount.get();
    }

    int drainPendingRequestCount() {
        return pendingRequestCount.getAndSet(0);
    }

    void restorePendingRequestCount(int drainedRequestCount) {
        pendingRequestCount.addAndGet(drainedRequestCount);
    }

    int getLastKnownGlobalRequestCount() {
        return lastKnownGlobalRequestCount;
    }

    void updateLastKnownGlobalRequestCount(int updatedGlobalRequestCount) {
        this.lastKnownGlobalRequestCount = updatedGlobalRequestCount;
    }

    boolean tryStartFlush() {
        return flushInProgress.compareAndSet(false, true);
    }

    void finishFlush() {
        flushInProgress.set(false);
    }

    boolean isSameWindow(long windowTimestamp) {
        return this.windowStartTimestamp == windowTimestamp;
    }

}
