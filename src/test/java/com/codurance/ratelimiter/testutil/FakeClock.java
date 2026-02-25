package com.codurance.ratelimiter.testutil;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

public class FakeClock extends Clock {

    private long currentMillis;

    public FakeClock(long initialMillis) {
        this.currentMillis = initialMillis;
    }

    public void advanceMillis(long millis) {
        currentMillis += millis;
    }

    public void advanceSeconds(long seconds) {
        advanceMillis(seconds * 1000);
    }

    @Override
    public ZoneId getZone() {
        return ZoneId.systemDefault();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return Instant.ofEpochMilli(currentMillis);
    }

    @Override
    public long millis() {
        return currentMillis;
    }
}
